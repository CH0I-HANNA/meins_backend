# 소유권 이전(Ownership Transfer) 구현 계획

## Context

중고 거래로 넘겨받은 제품의 소유권을 새 소유자에게 이전하는 기능. 사용자가 공유한 기획 명세(02 게스트뷰 / 03 코드입력 / 04 오너뷰-홈 / 05 오너뷰-소유권 / "추가) 소유권 이전" / 공통규칙)를 기준으로 백엔드(도메인 `ownership`, `chat`, 인증 인터셉터)에 필요한 변경만 다룬다. 프론트엔드는 이 레포 범위 밖.

**핵심 설계 이슈**: 명세의 "이전 토큰 무효화"(이전 소유자 토큰 즉시 실효)를 구현하려면 현재 오너 토큰(`mcm:own:{tagCode}:{authCode}`)의 `authCode`를 그대로 쓸 수 없다 — `authCode`는 실물에 인쇄된 값이라 양도돼도 절대 바뀌지 않고, 관리자 `UNLOCK_RECOVERY`로 재등록 상태로 되돌릴 때 실물 코드와 DB가 다시 일치해야 하기 때문이다(`AdminTagService.forceStatus` UNLOCK_RECOVERY/UNREGISTERED 케이스).

→ **결정(사용자 확인 완료, 아직 운영 전이라 하위호환 불필요)**: `OwnershipRecord`에 `ownerSecret`(랜덤, 등록/이전마다 재발급)을 추가하고, 오너 토큰을 `mcm:own:{tagCode}:{ownerSecret}`로 바꾼다. `authCode`는 최초 구매 인증(오프라인 실물 검증)에만 쓰이고, 발급된 토큰과는 완전히 분리된다. 이전 시 `ownerSecret`을 재발급하면 이전 소유자의 캐시된 토큰은 자동으로 무효가 된다.

## 변경 대상 파일

### 1. `common/util/RandomCodeGenerator.java` (신규)
`AdminTagService`에 있던 `SecureRandom` 기반 코드 생성 로직(`randomCode`, 알파벳 상수)을 여기로 옮겨 공용화. `AdminTagService`(태그/인증코드 생성)와 `OwnershipService`(양도코드/ownerSecret 생성)가 함께 쓴다.
- `ALPHANUMERIC_NO_AMBIGUOUS` (0/O/1/I 제외, 사람이 직접 입력하는 코드용 — 인증코드·양도코드·ownerSecret 공통)
- `randomCode(String alphabet, int length)`

`AdminTagService`는 이 유틸을 호출하도록 리팩터링(동작 변화 없음, 중복 제거).

### 2. `domain/ownership/entity/OwnershipRecord.java`
필드 추가: `ownerSecret`(String), `transferCode`(String, nullable), `transferCodeIssuedAt`(LocalDateTime, nullable), `transferCount`(int, default 0).

메서드:
- `markRegistered(ipHash, registeredAt, ownerSecret)` — 시그니처에 `ownerSecret` 추가
- `resetForRecovery()` — `ownerSecret`/`transferCode`/`transferCodeIssuedAt`도 함께 초기화
- `hasActiveTransferCode(LocalDateTime now)` — `transferCode != null && now.isBefore(transferCodeIssuedAt.plusHours(24))`
- `getTransferCodeExpiresAt()` — `transferCodeIssuedAt + 24h` (없으면 null)
- `issueTransferCode(code, now)` / `cancelTransferCode()` — `transferCode`/`transferCodeIssuedAt` null 처리
- `applyTransfer(ipHash, now, newOwnerSecret)` — 레코드 "교체": ipHash·registeredAt 갱신, ownerSecret 재발급, transferCode 초기화, `transferCount++`

컬럼은 `ddl-auto=update`로 자동 생성됨 (수동 마이그레이션 불필요).

### 3. `domain/chat/entity/ChatCredit.java`
- `TRANSFER_LIMIT = 15` 상수 추가
- `limit` 컬럼 추가(`@Column(name = "credit_limit")` — `LIMIT`은 MySQL 예약어라 컬럼명 매핑 필요). 현재 `ChatHistoryResponse`에 내려가는 "총 크레딧"이 실제 엔티티 값이 아니라 `ChatCredit.DEFAULT_LIMIT` 상수 하드코딩(`ChatHarnessService:114`)이라, 이전 후 remaining=15인데 limit=30으로 표시되는 불일치가 생김 — 엔티티가 자기 한도를 들고 있도록 고친다.
- `init()`이 `limit=DEFAULT_LIMIT(30)`도 세팅
- `resetForTransfer(now)` 신규 — `remaining=limit=TRANSFER_LIMIT(15)`

`ChatHarnessService.getChatHistory()`는 `findRemainingByTagCode` 대신 `findByTagCode`로 엔티티를 가져와 `credit.getLimit()`을 사용하도록 수정.

### 4. `global/interceptor/OwnerAuthInterceptor.java`
- `OwnershipRepository` 주입 추가
- 토큰 파싱은 동일(`{tagCode}:{secret}` 2세그먼트, 포맷 변화 없음), 다만 두 번째 세그먼트를 `ownerSecret`으로 해석
- `tag.getAuthCode().equals(tokenAuthCode)`(현재 비-상수시간 비교) → `record.getOwnerSecret()`을 `MessageDigest.isEqual`로 상수시간 비교하도록 교체 (기존 `OwnershipService.constantTimeEquals`/`AdminAuthInterceptor`와 동일 패턴 — 겸사겸사 기존의 비상수시간 비교 틈도 막음)
- `tag.isRegistered()` 게이트는 유지

### 5. `domain/ownership/service/OwnershipService.java`
- `register()`: `RandomCodeGenerator`로 `ownerSecret` 생성해 `record.markRegistered(ipHash, now, ownerSecret)`에 전달, `OwnershipResponse.of(tagCode, ownerSecret, registeredAt)`로 토큰 조립
- `issueOrFetchTransferCode(rawTagCode)` (신규) — 활성 코드 있으면 그대로 반환(재발급 아님), 없으면 `RandomCodeGenerator`로 12자 생성 후 `record.issueTransferCode(...)`
- `cancelTransferCode(rawTagCode)` (신규) — `record.cancelTransferCode()` 후 save (활성 코드가 없어도 그냥 성공, no-op)
- `transfer(rawTagCode, rawCode, clientIp)` (신규) — `register()`와 동일한 뼈대 재사용:
  1. tagCode/code 정규화(`CodeNormalizer.normalizeAndValidateTagCode` / `normalizeAuthCode` 그대로 재사용 — 양도코드도 "인증코드와 동일 문자집합")
  2. `OwnershipAttempt`를 `tagCode+ipHash`로 조회해 잠금 판정(등록 플로우와 **동일 테이블 재사용** — 태그 상태상 구매인증 잠금과 양도코드 잠금이 동시에 겹칠 일이 없어 안전)
  3. `record.hasActiveTransferCode(now) && constantTimeEquals(record.getTransferCode(), code)`가 아니면 기존 `failAndMaybeLock(attempt, now, CODE_MISMATCH)` 그대로 호출 — 만료/취소/불일치 모두 동일한 `CODE_MISMATCH`로 수렴(명세: "만료 여부 미노출")
  4. 성공 시: `record.applyTransfer(ipHash, now, newSecret)`, `attempt.reset(now)`, `ChatCredit.resetForTransfer(now)`, `chatMessageRepository.deleteByTagCode(tagCode)`(챗 이력 미승계), `OwnershipResponse.of(...)`로 신규 토큰 반환
  - `@Transactional(noRollbackFor = BusinessException.class)` — `register()`와 동일한 이유(잠금 카운터는 실패해도 커밋되어야 함)
  - `ChatMessageRepository` 신규 주입 필요

### 6. DTO
- `domain/ownership/dto/TransferCodeResponse.java` (신규): `{ code, issuedAt, expiresAt }` (KST ISO)
- 양도코드 검증 요청/응답은 기존 `OwnershipRequest`(`{code}`)/`OwnershipResponse`(`{token, record.registeredAt}`) 그대로 재사용 — 모양이 동일해 새 DTO 불필요

### 7. `domain/ownership/controller/OwnershipController.java`
3개 엔드포인트 추가:
- `POST /api/tags/{tagCode}/ownership/transfer-code` — 오너 인증 필요, 발급/재조회(있으면 기존 것 반환)
- `DELETE /api/tags/{tagCode}/ownership/transfer-code` — 오너 인증 필요, 발급 취소
- `POST /api/tags/{tagCode}/ownership/transfer` — 인증 불필요(등록 엔드포인트와 동일한 이유 — 새 소유자는 아직 토큰이 없음)

### 8. `global/config/WebMvcConfig.java`
`ownerAuthInterceptor` 경로 패턴에 `/api/tags/*/ownership/transfer-code` 추가. `/api/tags/*/ownership/transfer`는 등록(`POST /ownership`)과 마찬가지로 **추가하지 않음**(비인증).

## 명세 항목 ↔ 구현 매핑 (누락 점검용)

| 명세 | 처리 |
|---|---|
| 02 "양도받으셨나요" 링크는 REGISTERED 상태에서만 | 기존 `GET /api/tags/{tagCode}` 응답의 `ownership.registered`로 이미 충분 (변경 없음) |
| 03 구매인증/양도이전 화면 공유 | 두 코드 모두 `CodeNormalizer.normalizeAuthCode` 재사용, 형식 동일 |
| 04 코드 없음 시 행 숨김 | 프론트 전용 — 별도 백엔드 필드 불필요(발급 상태는 명세상 오너가 직접 "이전하기" 눌러야 알 수 있으므로 홈 응답에 상시 노출할 필요 없음) |
| 04 토큰 무효 → 게스트 강등 | 기존 `TOKEN_INVALID` 401 흐름 그대로 — ownerSecret 회전만으로 이전 소유자 토큰이 자동 실효 |
| 05 이전 모달 재진입 시 기존 코드 표시 | `issueOrFetchTransferCode`가 활성 코드면 그대로 반환 |
| 05 발급 취소 → 재발급 가능 | `cancelTransferCode` → `transferCode=null` |
| 05 판매자 재진입(사용 완료 후) | 토큰 실효로 자동 처리, 별도 로직 불필요 |
| 신규 레코드 크레딧 15턴, 챗 이력 미승계 | `ChatCredit.resetForTransfer` + `chatMessageRepository.deleteByTagCode` |
| 이전 횟수만 누적, 이전 소유자 식별정보 미보관 | `transferCount++`만 증가, ipHash 외 개인식별값 저장 안 함(기존 패턴과 동일) |
| 만료/취소 코드 → 불일치와 동일 처리 | `hasActiveTransferCode` 거짓이면 무조건 `CODE_MISMATCH` |
| 5회 실패 → 24시간 잠금 | 기존 `OwnershipAttempt` 테이블/로직 재사용 |

## 검증 방법

1. `./gradlew build` (또는 `bootRun`)로 컴파일 및 `ddl-auto=update`로 신규 컬럼 반영 확인
2. `POST /admin/tags/bulk-create`로 테스트 태그 생성 → `POST /api/tags/{tagCode}/ownership`로 구매 등록해 토큰 A 발급
3. 토큰 A로 `POST /api/tags/{tagCode}/ownership/transfer-code` 호출 → 양도코드 확인, 재호출 시 동일 코드 반환되는지 확인
4. `POST /api/tags/{tagCode}/ownership/transfer`에 그 코드로 요청 → 새 토큰 B 발급 확인
5. 토큰 A로 `/ownership/me` 또는 `/chat/history` 호출 → `401 TOKEN_INVALID` 확인 (구 토큰 실효)
6. 토큰 B로 `/chat/history` 호출 → `credits.remaining=15`, `credits.limit=15`, `messages=[]`(이전 대화 미승계) 확인
7. 틀린 양도코드 5회 입력 → `CODE_LOCKED` + 24시간 잠금 확인 (등록 플로우와 동일 락 테이블 공유 여부도 같이 확인)
8. 만료/취소된 코드로 시도 시 `CODE_MISMATCH`로 동일하게 처리되는지 확인