# MCM Onboarding Backend — 개발 문서

QR 코드를 스캔해 명품(MCM) 제품의 소유권을 등록하고, 소유자 전용 AI 챗봇과 대화할 수 있게 하는 온보딩 백엔드다. 해커톤 MVP 성격의 하네스로, "규칙을 코드가 아니라 아키텍처 레벨에서 강제한다"는 목표로 설계됐다.

## 1. 기술 스택

| 영역 | 선택 |
|---|---|
| 언어 / 런타임 | Java 17 |
| 프레임워크 | Spring Boot 4.1.0 |
| 웹 계층 | Spring MVC(REST) + WebFlux(WebClient, LLM 호출 전용) |
| 영속성 | Spring Data JPA + MySQL (`meins_onboarding`) |
| API 문서화 | springdoc-openapi 2.8.5 (Swagger UI) |
| 빌드 | Gradle |
| 기타 | Lombok, Bean Validation |

빌드 설정(`build.gradle`)에는 `springdoc-openapi-starter-webmvc-ui:2.8.5`만 허용하고 `3.0.0 사용 금지`라는 주석이 명시되어 있다 — Spring Boot 4.x와의 호환성 문제로 버전을 고정한 것으로 보인다.

## 2. 패키지 구조

```
com.mcm.onboarding
├── common
│   ├── dto/ErrorResponse.java          # { code, message, traceId } 공통 에러 포맷
│   └── exception/
│       ├── BusinessException.java      # ErrorCode를 감싸는 단일 커스텀 예외
│       ├── ErrorCode.java              # 모든 에러 코드/HTTP 상태/메시지 enum
│       └── GlobalExceptionHandler.java # @RestControllerAdvice
├── domain
│   ├── product/       # 제품 마스터 정보 (관리자가 배치 단위로 등록)
│   ├── tag/            # 태그(QR) 조회 — 실물 1개당 1건, Product를 참조
│   ├── ownership/       # 소유권 등록 + 오너 토큰 발급
│   ├── chat/             # AI 챗 SSE 스트리밍 + 크레딧 + 히스토리
│   └── admin/             # 관리자: 제품/태그 일괄 생성, QR 발급, 상태 강제 변경
└── global
    ├── config/         # OpenApiConfig, WebClientConfig, WebMvcConfig
    └── interceptor/     # OwnerAuthInterceptor, AdminAuthInterceptor
```

도메인은 `controller → service → repository` 3계층 구조를 따르고, 각 도메인이 `dto`/`entity`를 함께 가진다. `common`과 `global`은 도메인을 가로지르는 공통 관심사(에러, 인증, 설정)를 담당한다.

## 3. 도메인별 흐름

### 3-1. Product / Tag — 제품·태그 조회 (`domain/product`, `domain/tag`)
- `Product`: 관리자가 배치 단위로 등록하는 마스터 정보 — `productName`, `manufacturedYm`(YYYY-MM), `material`, `color`, `saleRegisteredYm`(판매 등록 연월, YYYY-MM), `widthCm`/`depthCm`/`heightCm`(가로/세로/높이, cm 정수).
- `Tag`: 실물 QR 1개당 1행. `product`(FK), `tagCode`(unique, `XXXX-XXXX` 형식·영문+숫자, QR에 인코딩되는 공개 식별자), `authCode`(unique, `XXXX-XXXX-XXXX` 형식·`0`/`O`/`1`/`I` 제외, 실물에만 인쇄되는 비공개 2차 인증 코드 — 소유권 등록 시 필수 검증), `status`(`UNREGISTERED`/`REGISTERED`, 등록 상태의 단일 진실 공급원).
- **대소문자 무시**: tagCode/authCode는 항상 대문자로 저장되고, 사용자 입력(경로변수/요청바디/토큰)은 `CodeNormalizer.normalize()`로 대문자 정규화한 뒤 비교·조회한다 — 소문자로 입력해도 동작.
- `GET /api/tags/{tagCode}` — 인증 불필요. `TagDetailResponse`로 `product`/`official`/`ownership` 중첩 구조를 반환한다(`authCode`는 노출 안 함). 프론트는 `ownership.registered`로 "이미 등록된 제품입니다" 안내와 "인증 코드 입력"을 분기한다. `ownership.registeredAt`은 **게스트 정밀도(`YYYY-MM`)**로만 내려간다.
- `GET /api/tags/{tagCode}/ownership/me` — 오너 전용. `OwnerAuthInterceptor` 통과 필요. `OwnerHomeResponse`(`record`/`product`/`official`)를 반환하며 `record.registeredAt`은 **ISO 8601 + KST 오프셋**(`2026-03-14T09:22:00+09:00`)이다. 오너 홈이 게스트 뷰와 같은 정보 + 등록 카드이므로 `product`/`official`을 함께 내려 호출 1회로 끝낸다(05 소유권 화면은 이 응답을 재사용).
- **정밀도 마스킹은 프론트가 아니라 서버가 한다**: `TagService.getTagInfo()`/`getOwnerHome()`이 각각 `KstTime.toGuestPrecision()`/`toIso()`로 `OwnershipRecord.registeredAt`을 포맷해서 응답에 담는다. 게스트 엔드포인트가 애초에 분단위 값을 응답에 넣지 않으므로, 프론트가 표시를 어떻게 하든(또는 API를 직접 호출하든) 오너가 아니면 분단위 시각을 알 수 없다 — "인가된 사람에게만 정밀 데이터를 내려준다"는 원칙을 응답 생성 시점에 강제.
- **형식 검증**: tagCode는 `XXXX-XXXX` 정규식으로 먼저 검증하고 어긋나면 DB 조회 전에 `TAG_INVALID_FORMAT`(400)으로 끊는다. 열거 방지를 위해 `TAG_NOT_FOUND`와 메시지가 동일하다.

### 3-2. Ownership — 소유권 등록 (`domain/ownership`)
- `POST /api/tags/{tagCode}/ownership` — body에 `code`(인증 코드) 필수. 인증 헤더는 불필요(등록 이전 단계이므로)하지만, 서버가 DB에 저장된 `Tag.authCode`와 대조해 일치할 때만 등록을 진행한다 — "QR만 스캔하면 누구나 선점"하는 것을 막는 핵심 게이트. 입력은 대문자 정규화 + 하이픈 제거 후 비교하므로 `XXXX-XXXX-XXXX`/`XXXXXXXXXXXX` 둘 다 받는다.
- 성공 시 `mcm:own:{tagCode}:{authCode}` 형태의 Bearer 토큰과 소유 레코드(`record.registeredAt`, ISO 8601)를 반환하고 초기 크레딧 30을 발급한다. tagCode는 QR로 공개되지만 authCode는 등록 절차를 통과해야만 알 수 있으므로, 토큰은 등록하지 않은 사람이 임의로 계산할 수 없다.
- **`OwnershipRecord`는 Tag와 1:1로, 관리자가 태그를 생성하는 시점에 항상 함께 만들어진다** (`AdminTagService.bulkCreate`) — 이전처럼 "레코드가 없을 수도 있는" null 케이스를 서비스 로직에서 다룰 필요가 없어졌다. 소유 레코드는 등록 IP 해시와 등록 시각만 담당한다.
- **5회 실패 잠금 정책**: 실패 횟수와 잠금은 `OwnershipRecord`가 아니라 별도 엔티티 `OwnershipAttempt`가 **`(tagCode, ip_hash)` 조합**으로 추적한다. tagCode 단독으로 잠그면 제3자가 남의 태그에 아무 코드나 5번 쳐서 잠글 수 있기 때문(기획 명세 2-2가 직접 지적한 취약점). "이미 등록된 태그 재시도"와 "인증 코드 불일치"가 **같은 실패 카운트를 공유**하며(브루트포스 방지 목적이 동일하므로), 5회째에 `CODE_LOCKED(429)` + `lockedUntil`로 전환된다. 잠금은 **24시간 뒤 자동 해제**되고, 등록에 성공하면 카운트가 리셋된다. `@Transactional(noRollbackFor = BusinessException.class)`로 실패 카운트 증가가 예외 발생 시에도 커밋되도록 보장한다.
- 실패 응답에는 프론트가 쓸 부가 정보가 실린다 — `CODE_MISMATCH`에 `remainingAttempts`, `CODE_LOCKED`에 `lockedUntil`. 프론트가 자체 카운터를 두면 시크릿 창으로 우회되므로 서버 값만 표시하게 한다.
- 이미 사용된 코드는 `CODE_MISMATCH`와 동일하게 처리된다(사용 여부가 노출되면 코드 열거 단서가 되므로).
- 등록 성공 시 `Tag.markRegistered()` 호출과 `ChatCredit` 초기화(중복 방지를 위해 `findByTagCode` 후 없을 때만 `init`)가 같은 트랜잭션에서 함께 처리된다.

### 3-3 / 3-4 / 3-5. Chat — AI 챗 SSE 스트리밍 (`domain/chat`)
`ChatHarnessService.streamChat()`이 하네스의 핵심 오케스트레이터로, CLAUDE.md에 명시된 3계층 순서를 그대로 코드로 구현한다.

```
Layer 1 (가드레일)  CreditGuardService.checkCredit()
                     └─ remaining <= 0 이면 LLM 호출 전에 즉시 CREDIT_EXHAUSTED(429)
Layer 2 (컨텍스트)   buildSystemPrompt(tagCode, preset)
                     └─ DB에서 Tag 재조회 → 가드레일 상수 + 제품 컨텍스트 + 프리셋 컨텍스트 조립
                     └─ 프론트가 보낸 컨텍스트는 절대 신뢰하지 않고 서버가 tagCode로 직접 조회
Layer 3 (실행)       LlmWebClient.streamCompletion() → SseEmitter로 청크 전달
```

- **가드레일 프롬프트**는 `GUARDRAIL_PROMPT` 상수로 고정되어 매 요청마다 시스템 프롬프트 최상단에 강제 주입된다 (가품 판정 금지 / 가격 산정 금지 / 리셀 시세 언급 금지).
- **프리셋**: `care`(관리법) / `style`(스타일링) / `heritage`(브랜드 헤리티지) 중 하나를 요청 시 지정하면 해당 문맥이 시스템 프롬프트에 추가된다. 없으면 "일반 문의"로 처리.
- **크레딧 차감**: LLM 호출 직전에 `reserveCredit()`으로 1턴을 선차감한다(`@Transactional` + 원자적 UPDATE `remaining = remaining - 1 WHERE remaining > 0`, 영향 row 수로 소진 판정 — 조회와 차감 사이 레이스를 여기서 닫는다). 명세상 "호출 실패 시 미차감"이므로 LLM 스트림이 에러로 끝나는 `onError` 경로에서만 `refundCredit()`으로 되돌린다. 정상 완료와 클라이언트 중단(abort)은 명세상 모두 차감 대상이라 되돌리지 않는다.
- **연결 끊김(Abort) 대응**: `emitter.onCompletion/onTimeout/onError`에서 모두 `subscription.dispose()`를 호출해, 클라이언트가 연결을 끊어도 서버가 붙잡고 있던 LLM WebClient 구독을 즉시 취소한다. 불필요한 LLM 비용 지출을 막기 위한 장치.
- `LlmWebClient`는 현재 실제 LLM 호출 대신 더미 텍스트를 150ms 간격으로 스트리밍하는 자리표시자 구현이며, 코드 내 주석으로 실제 OpenAI `/chat/completions` 스트리밍 연동 예시가 남겨져 있다.
- `GET /api/tags/{tagCode}/chat/history` — 오너 전용. `{ messages: [{role, content, createdAt}], credits: { remaining, limit } }` 형태로 대화 내역과 크레딧 잔량을 함께 반환한다(프론트는 `remaining <= 2`일 때 안내 문구를 띄운다). 크레딧 회복 정책이 미확정이라 `credits.resetAt`은 현재 항상 생략된다. `messages`는 `tagCode` 기준으로 서버에 저장된 실제 대화 내역이다(재진입 시 복원됨).

## 4. 인증

### 4-1. 오너 인증 (`OwnerAuthInterceptor`)
- `WebMvcConfig`에 `HandlerInterceptor`로 등록되며, 오너 인증이 필요한 경로(`/api/tags/*/ownership/me`, `/api/tags/*/chat`, `/api/tags/*/chat/**`)에만 적용된다. 소유권 등록(`POST /api/tags/*/ownership`)은 이 패턴에 걸리지 않으므로 인증 없이 호출된다.
- 검증 로직: `Authorization` 헤더가 `Bearer mcm:own:` 접두사로 시작하는지 확인 → 토큰을 `{tagCode}:{authCode}`로 파싱 → URL 경로의 tagCode와 일치하는지 확인 → **DB에서 해당 tagCode의 Tag를 조회해 `authCode` 일치 + `status == REGISTERED`까지 실제로 검증**.
- 헤더 누락/형식 오류/authCode 불일치/미등록 상태/다른 태그의 토큰 → 전부 `TOKEN_INVALID(401)`. 실패 원인을 구분해 노출하지 않는다(열거 방지, 기획 명세 1-2의 단일 코드 정책).
- 이 인터셉터가 하네스 규칙의 "프론트 입력 무신뢰" 원칙을 API 게이트웨이 레벨에서 강제하는 지점이다.
- **수정 이력**: 원래는 토큰 형식(`mcm:own:{tagCode}`)만 확인하고 DB 검증이 없어서, tagCode(QR로 공개됨)만 알면 `POST /ownership`을 호출하지 않고도 `/home`·`/chat`에 접근 가능한 구멍이 있었다. authCode를 토큰에 포함시키고 매 요청마다 DB 대조를 추가해 해결 — 등록 절차를 실제로 통과한 사람만 토큰을 계산할 수 있다.

### 4-2. 관리자 인증 (`AdminAuthInterceptor`)
- `/admin/**` 전체에 적용되는 고정 키 방식. `X-Admin-Key` 헤더 값을 환경변수 `ADMIN_KEY`(`admin.api-key`)와 단순 비교한다.
- 키 누락/불일치 → `INVALID_ADMIN_KEY(401, AUTH_003)`.
- Basic Auth/Spring Security 대신 기존 `OwnerAuthInterceptor`와 동일한 인터셉터 패턴을 재사용 — 계정 개념(다중 관리자, 세션)이 필요 없는 해커톤 MVP 스코프에 맞춘 선택.

## 5. 에러 처리

모든 에러는 `ErrorCode` enum(HTTP 상태 + 코드 문자열 + 메시지) → `BusinessException`으로 감싸 throw → `GlobalExceptionHandler`가 `{ code, message, traceId }` 형태의 `ErrorResponse`로 변환하는 단일 경로를 따른다. 도메인 코드에서 `ResponseEntity`를 직접 에러로 만들거나 새로운 예외 타입을 throw하는 대신, 이 경로만 사용하도록 통일되어 있다. Bean Validation 실패(`MethodArgumentNotValidException`)도 같은 포맷으로 내려간다.

**코드 문자열과 HTTP 상태는 기획 명세 "API > 1-2. 에러 응답 포맷" 표를 그대로 따른다** — 프론트가 이 값으로 화면을 분기하므로 임의로 바꾸지 말 것: `TAG_NOT_FOUND`(404) / `TAG_INVALID_FORMAT`(400) / `CODE_MISMATCH`(400) / `CODE_LOCKED`(429) / `ALREADY_REGISTERED`(409) / `TOKEN_INVALID`(401) / `CREDIT_EXHAUSTED`(429) / `INTERNAL_ERROR`(500). 관리자 도구 전용으로 `ADMIN_KEY_INVALID`(401) / `ADMIN_INVALID_ACTION`(400)이 추가로 있다.

일부 에러에는 부가 필드가 실린다 — `CODE_MISMATCH` → `remainingAttempts`, `CODE_LOCKED` → `lockedUntil`, `CREDIT_EXHAUSTED` → `resetAt`(정책 미확정이라 현재는 생략됨). 해당 없으면 `@JsonInclude(NON_NULL)`로 키 자체가 빠진다.

## 5-1. 관리자 흐름 (`domain/admin`)

```
관리자 페이지 접속 (X-Admin-Key 고정 키)
  → POST /admin/tags/bulk-create   (제품 정보 + 수량 → Product 1건 + Tag/OwnershipRecord N건 일괄 생성)
  → GET  /admin/tags                (목록 조회, 태그별 qrImageUrl 포함)
  → GET  /admin/qr/{tagCode}         (QR PNG 즉석 생성 — DB에 이미지 저장 안 함)
  → GET  /admin/tags/qr-export        (전체 QR을 zip으로 묶어 다운로드)
  → (프린트 후 실물 부착 → 판매)

[운영 중]  POST /admin/tags/{tagCode}/force-status { action: UNLOCK | UNLOCK_RECOVERY }
[데모 준비] POST /admin/tags/{tagCode}/force-status { action: REGISTERED | UNREGISTERED }
```

- `AdminTagService.bulkCreate`: tagCode(`XXXX-XXXX`, 영문+숫자 8자)와 authCode(`XXXX-XXXX-XXXX`, `0`/`O`/`1`/`I` 제외 12자)를 `SecureRandom`으로 생성하고 유니크 제약 위반 시 재시도. 생성 즉시 `OwnershipRecord`도 `UNREGISTERED` 상태로 함께 저장 — Tag만 있고 OwnershipRecord가 없는 상태가 존재하지 않도록 보장.
- `QrCodeService`: ZXing으로 PNG를 즉석 생성. 인코딩 대상은 `app.qr.url-template`(기본값 `{tagCode}` 그대로) — 프론트엔드 도메인이 정해지면 `https://.../t/{tagCode}` 형태로 env var(`QR_URL_TEMPLATE`)만 바꾸면 된다.
- `force-status` 4가지 액션:
  - `UNLOCK`: 해당 태그에 쌓인 `OwnershipAttempt`(시도 이력)를 전부 삭제. 등록 상태는 유지 (정상 오너가 재시도 횟수만 초과한 경우). 24시간이 지나면 어차피 자동 해제되므로 즉시 풀어줄 때만 쓴다.
  - `UNLOCK_RECOVERY`: 위에 더해 `ipHash`/`registeredAt`까지 초기화하고 `Tag.status`를 `UNREGISTERED`로 되돌림 — 완전히 처음부터 재등록 가능한 상태로 복구.
  - `REGISTERED`: 실제 등록 절차 없이 `status=REGISTERED` + `ChatCredit` 초기화만 수행 (없으면 생성). 데모 리허설에서 "이미 등록된 상태"를 즉시 재현할 때 사용.
  - `UNREGISTERED`: `status=UNREGISTERED`로 리셋하고 `ChatCredit`/`ChatMessage`까지 삭제 — 데모를 처음 상태로 완전히 되돌릴 때 사용.
- `AdminTagService.bulkCreate`가 생성하는 authCode는 하이픈 없는 12자 연속 문자열이다. 서버가 입력의 하이픈을 제거하고 비교하므로 실물에는 `XXXX-XXXX-XXXX`로 인쇄해도 무방하다.

## 6. API 문서화 (Swagger)

- `OpenApiConfig`에서 `OwnerToken`이라는 이름의 Bearer 시큐리티 스킴을 전역 등록하고, 인증이 필요한 각 엔드포인트에 `@SecurityRequirement(name = "OwnerToken")`을 명시.
- 모든 컨트롤러 메서드에 `@Operation`/`@ApiResponses`로 한국어 설명, 요청/응답 예시, 에러 코드 매핑이 상세히 달려있어 Swagger UI 자체가 API 명세서 역할을 한다.
- 경로: Swagger UI `/swagger-ui.html`, OpenAPI JSON `/v3/api-docs` (`application.properties`에서 설정).

## 7. 설정 (`application.properties`)

- MySQL 연결 정보(`meins_onboarding` 스키마), `ddl-auto=update`로 엔티티 변경 시 자동 스키마 반영.
- `llm.api.base-url`/`llm.api.key`로 LLM 연동 설정을 외부화(현재 key는 비어있음 — `LlmWebClient`가 더미 응답만 반환하는 이유).
- SSE 비동기 타임아웃 120초(`spring.mvc.async.request-timeout`), `SseEmitter` 자체 타임아웃은 60초로 코드에 별도 설정.
- `spring.datasource.password`, `llm.api.key` 등 민감값은 `${ENV_VAR:default}` 플레이스홀더로 분리되어 있고, 실제 값은 `application.properties`(커밋 대상)가 아니라 `.idea/workspace.xml`의 `MeinsOnboardingApplication` Run Configuration(`.idea`는 `.gitignore` 대상)에만 존재한다.

| 환경변수 | 필수 여부 | 기본값 |
|---|---|---|
| `DB_URL` | 선택 | `jdbc:mysql://localhost:3306/meins_onboarding?...` |
| `DB_USERNAME` | 선택 | `root` |
| `DB_PASSWORD` | **필수** (기본값 없음) | 없음 |
| `LLM_API_BASE_URL` | 선택 | `https://api.openai.com/v1` |
| `LLM_API_KEY` | 선택 | 빈 문자열 (더미 응답만 동작) |
| `ADMIN_KEY` | **필수** (기본값 없음) | 없음 — `/admin/**` 호출 시 `X-Admin-Key` 헤더 값과 비교 |
| `QR_URL_TEMPLATE` | 선택 | `{tagCode}` (QR에 tagCode 원문만 인코딩) |

로컬에서 IDE 없이 `./gradlew bootRun`/`test`를 직접 돌릴 때는 `DB_PASSWORD=... ADMIN_KEY=... ./gradlew bootRun`처럼 인라인으로 넘겨야 한다. IntelliJ에서는 `MeinsOnboardingApplication` Run Configuration의 Environment variables에 이미 설정되어 있다.

> `spring-dotenv` 의존성이 추가되어 있지만 이 프로젝트의 Spring Boot 4.1.0에서 `.env` 자동 로딩이 실제로 동작하는지는 아직 검증되지 않았다 — 현재는 IntelliJ Run Configuration의 환경변수가 실질적인 값 공급원이다.

## 8. 미완성 / TODO

- `LlmWebClient`: 더미 스트리밍 → 실제 OpenAI(or 다른 LLM) 스트리밍 API로 교체 필요 (연동 예시 코드는 주석으로 이미 준비됨).
- **크레딧 자동 회복(롤링 리셋) 없음** — 회복 주기가 미확정이라 구현하지 않았다. 확정되면 `credits.resetAt`과 `CREDIT_EXHAUSTED`의 `resetAt`을 함께 채운다.
- **IP 시간당 상한(`RATE_LIMITED`) 미구현**, **CORS 설정 없음**.
- 소유권 카드 이미지(`card.png`), OG 태그 제어, 판매 미등록 상태(`TAG_NOT_RELEASED`) 미구현.
- QR 인코딩 대상이 아직 tagCode 원문뿐 — 프론트엔드 도메인이 정해지면 `QR_URL_TEMPLATE` env var만 바꾸면 됨(코드 변경 불필요).
- authCode는 12자리 랜덤 문자열을 그대로 평문 저장/비교한다. 5회 실패 잠금이 `(tagCode, ip_hash)` 단위로 1차 방어를 하지만, 실사용 단계에서는 전역 rate limit이나 authCode 엔트로피 상향을 고려할 수 있다.

### 완료됨
- ~~`com.example.luxury` 기본 애플리케이션 클래스 정리~~ — 삭제 완료. 해당 패키지의 `LuxuryApplicationTests`가 `@SpringBootConfiguration`을 찾지 못해 `./gradlew test`가 실패하던 상태였음. 삭제 후 `com.mcm.onboarding.McmOnboardingApplicationTests`로 컨텍스트 로딩 스모크 테스트를 대체 추가. 이때 `.idea/workspace.xml`에 남아있던 `LuxuryApplication` Run Configuration도 함께 정리.
- ~~DB/LLM 관련 민감 설정값의 환경변수 분리~~ — `application.properties`를 `${DB_PASSWORD}`, `${LLM_API_KEY:}` 등 플레이스홀더로 교체. 실제 값은 IntelliJ Run Configuration에만 저장(`.idea`는 gitignore 대상이라 커밋되지 않음).
- ~~관리자 흐름(제품 등록 → 태그 일괄 생성 → QR 발급/다운로드 → 상태 강제 변경) 구현~~ — `domain/product`, `domain/admin` 신설. `Tag`를 `Product` 참조 구조로 리팩터링하고, `OwnershipRecord`를 Tag와 1:1로 항상 존재하도록 변경. `curl`로 전체 흐름(생성 → 목록 → QR PNG/zip → 등록 → 5회 실패 잠금 → UNLOCK/UNLOCK_RECOVERY/REGISTERED/UNREGISTERED) 수동 검증 완료.
- ~~OwnershipService의 `OwnershipRecord == null` 방어 로직 제거~~ — bulk-create 시점에 1:1 보장되므로 불필요해짐.
- ~~`authCode` 2차 인증 적용~~ — `POST /ownership`이 body의 `authCode`를 `Tag.authCode`와 대조해야만 등록 진행. 토큰도 `mcm:own:{tagCode}:{authCode}`로 변경하고 `OwnerAuthInterceptor`가 매 요청마다 DB에서 authCode+`status==REGISTERED`를 실제 검증하도록 수정 — 이전엔 tagCode(QR 공개값)만 알면 등록 절차 없이 `/home`·`/chat` 접근이 가능했던 구멍을 막음. 부수 효과로 `UNLOCK_RECOVERY` 이후 예전 토큰도 `status` 체크에서 즉시 무효화됨(예전에 4-1에 적어뒀던 한계가 해소됨).
- ~~등록 성공 시 실패 카운트 리셋~~ — `OwnershipRecord.markRegistered()`가 `failureCount`도 0으로 초기화하도록 수정. 등록 전 잘못된 시도(공격 포함)가 등록 후 정상 오너의 잠금 임계치에 누적되지 않음.
- ~~tagCode/authCode 포맷 표준화~~ — tagCode `XXXX-XXXX`(영문+숫자), authCode `XXXX-XXXX-XXXX`(`0`/`O`/`1`/`I` 제외)로 생성 로직 재작성(`AdminTagService`). 둘 다 대소문자 무시 — `CodeNormalizer` 유틸을 만들어 사용자 입력이 들어오는 모든 지점(컨트롤러 경로변수를 받는 서비스 진입점, 토큰 파싱)에서 대문자로 정규화 후 비교/조회하도록 통일.
- ~~Product 필드 정리~~ — `onSale`(boolean) → `saleRegisteredYm`(YYYY-MM), `size`(자유 문자열) → `widthCm`/`depthCm`/`heightCm`(정수, cm)로 변경. `BulkCreateRequest`/`TagDetailResponse`/`ChatHarnessService`(시스템 프롬프트의 제품 컨텍스트) 연쇄 반영.
- ~~소유 등록 시점(`registeredAt`) 노출 + 권한별 정밀도 차등~~ — `TagDetailResponse`에 필드 추가. 게스트(`GET /api/tags/{tagCode}`)는 `YYYY-MM`, 오너(`GET /home`)는 `YYYY-MM-DD HH:mm`로 **서버가** 포맷해서 내려줌 — 프론트가 표시만 다르게 하는 방식이 아니라, 애초에 게스트 응답에는 분단위 정보 자체가 담기지 않음(직접 API 호출로도 우회 불가).
- **서비스명 `meins`로 변경 시도 → 롤백됨 (미완료)** — Java 패키지 `com.mcm.onboarding` → `com.meins.onboarding` 전환을 시도했으나 빌드 에러가 반복 발생해 되돌렸다. 현재 코드는 여전히 `com.mcm.onboarding`이고, `settings.gradle`의 `rootProject.name`(`luxury`), `build.gradle`의 `group`(`com.example`), `spring.application.name`(`mcm-onboarding`)도 그대로다 — 네이밍이 4종류로 혼재된 상태. 재시도 전에 지난번 에러 원인부터 파악할 것.
- **DB 스키마명/애플리케이션명 변경 시도 → 함께 롤백됨 (미완료)** — 위 패키지 리네임과 같은 작업 단위로 `meins_onboarding` 스키마 생성, `spring.application.name`을 `meins-onboarding`으로 바꾸는 것도 같이 시도했으나 리네임 롤백과 함께 되돌아갔다. 현재 `application.properties`는 여전히 `spring.application.name=mcm-onboarding`, `DB_URL` 기본값도 `mcm_onboarding` 스키마를 가리킨다.
