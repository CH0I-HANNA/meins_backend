# 챗 기능

AI 담당자가 프롬프트/모델을 확정하지 못한 상태에서 백엔드가 어떻게 먼저 진행했고, 지금 무엇이 붙어 있고 무엇이 남았는지 정리한 문서.

## 1. 지금 상태: LLM 연동 완료, 프롬프트 문구만 남음

`ChatHarnessService.streamChat()`이 처리하는 3계층이 전부 실제 구현으로 채워졌다.

```
Layer 1 (가드레일)  CreditGuardService.checkCredit() / reserveCredit()   ✅ 완성
Layer 2 (컨텍스트)   ChatHarnessService.buildSystemPrompt()               ✅ 완성 (문구는 임시)
Layer 3 (실행)       LlmWebClient.streamCompletion()                     ✅ OpenAI 스트리밍 (PR #4, 2026-08-14)
```

인증, 크레딧 차감/환불, DB 컨텍스트 주입, SSE 스트리밍, 대화 히스토리 저장까지 전부 실제로 동작한다. 남은 건 `GUARDRAIL_PROMPT`를 AI 담당자가 검증한 문구로 교체하는 것뿐이다.

> **`LLM_API_KEY`가 없으면 챗이 동작하지 않는다.** PR #4에서 더미 응답이 제거됐기 때문에, 키가 비어 있으면 `Bearer ` 헤더로 요청이 나가 OpenAI가 401을 돌려주고 스트림이 끊긴다. 로컬 개발·프론트 SSE 연동 테스트에도 키가 필요하다.

## 2. AI 파트를 기다리는 동안 한 일 (기록)

- **통합 지점(interface)을 먼저 고정했다.** `streamCompletion(String systemPrompt, String userMessage) -> Flux<String>` 시그니처를 확정해둔 덕분에, 실제 연동은 `LlmWebClient` 한 파일 교체로 끝났다. 크레딧·컨텍스트·SSE 계층은 손대지 않았다.
- **더미 스트리밍으로 나머지를 전부 검증했다.** 프론트 SSE 연동, abort 처리, 히스토리 조회, 크레딧 소진 흐름을 AI 파트 없이 먼저 끝냈다. (더미는 PR #4에서 제거됐다 — 위 경고 참고)
- **AI 담당자에게 코드가 아니라 3가지를 요청한다.**
  1. 검증된 시스템 프롬프트 문구 (톤·가드레일 지시·few-shot 예시가 있다면 그것까지)
  2. 모델명 + 파라미터 (`model`, `temperature` 등)
  3. API 키 (Slack/코드로 공유 금지, Railway Variables에만)
- AI 담당자 레포가 실제 서버가 아니라면(프롬프트 실험 스크립트라면) **별도로 배포하지 않는다.** 크레딧/인증/컨텍스트 주입이 반드시 우리 백엔드에서 일어나야 하므로, 중간에 별도 서버를 끼우면 홉만 늘고 얻는 게 없다. → 우리 백엔드가 OpenAI를 직접 호출하는 구조로 간다.

## 3. 체크리스트

- [x] `LlmWebClient.streamCompletion()` 실제 구현으로 교체 — `WebClient`가 `ServerSentEvent<String>`으로 SSE를 직접 파싱하도록 구현(수동 줄 파싱 대신 Spring의 SSE 지원 사용), Jackson으로 각 청크의 `choices[0].delta.content`만 추출. `[DONE]`과 role-only/finish_reason-only 빈 청크는 필터링됨
- [x] 모델명 env var로 분리 — `llm.api.model` (`LLM_API_MODEL`, 기본값 `gpt-4o-mini`). `temperature`는 `LlmWebClient` 내부에 `0.7`로 하드코딩(자주 바꿀 값이 아니라 판단, 필요해지면 같은 방식으로 env var화)
- [x] 크레딧 환불 정책 — LLM **호출이 실패하면 선차감한 1턴을 환불**한다(PR #3). 정상 종료·클라이언트 중단은 1턴 차감 유지. 기획 명세 2-5 "호출 실패 시 미차감"과 일치
- [ ] **Railway에 `LLM_API_KEY` 설정** — 안 하면 배포본의 챗이 401로 죽는다. 최우선
- [ ] `ChatHarnessService.buildSystemPrompt()`의 `GUARDRAIL_PROMPT` / 제품 컨텍스트 / preset 컨텍스트에 AI 담당자가 검증한 프롬프트 문구 병합 — AI 담당자 결과물 도착 후 진행
- [ ] **스트리밍 에러 로깅 추가** — 현재 OpenAI가 401/429/5xx를 돌려줘도 `emitter.completeWithError()`로 스트림만 끊기고 서버 로그에 아무것도 남지 않는다. 데모 중 원인 파악이 불가능하므로 최소한 `doOnError` 로깅이 필요하다. `extractDeltaContent()`가 파싱 예외를 빈 문자열로 삼키는 것도 마찬가지
- [ ] ~~OpenAI 에러를 `BusinessException(ErrorCode)`로 매핑~~ → **구조적으로 불가능해서 방향 수정 필요**: 컨트롤러가 `SseEmitter`를 반환하는 순간 HTTP 200 + `text/event-stream` 헤더가 이미 커밋되기 때문에, 스트리밍 도중 발생하는 OpenAI 에러는 `GlobalExceptionHandler`를 못 타고 `{code,message,traceId}` JSON 바디로 못 내려간다. 지금은 `error -> emitter.completeWithError(error)`가 스트림을 끊는 걸로 그대로 둠 — 프론트가 이 경우(빈 응답으로 스트림 종료)를 어떻게 보여줄지는 별도 논의 필요
- [ ] `SseEmitter(60_000L)` 타임아웃이 실제 응답 속도에 맞는지 재검토
- [ ] AI 담당자가 검증했던 질문/시나리오를 실제 API로 재현해 톤·가드레일 준수 여부 확인

### 3-1. PR #4 머지 전에 잡은 이슈 2건

둘 다 컴파일은 통과하지만 실행하면 터지는 종류라 기록해둔다.

- **Jackson 2 → Jackson 3** (`f9facdf`): Spring Boot 4.1은 Jackson 3(`tools.jackson`)만 자동 설정한다. `JacksonAutoConfiguration`이 등록하는 빈은 `tools.jackson.databind.json.JsonMapper` 하나뿐이라, `com.fasterxml.jackson.databind.ObjectMapper`를 주입받으면 기동 시 `NoSuchBeanDefinitionException`으로 앱이 뜨지 않는다. Jackson 2 자체는 springdoc 등이 끌고 와 클래스패스에 있어서 **컴파일은 통과한다** — 새 라이브러리를 붙일 때 주의할 지점.
- **preset 전용 요청 NPE** (`1bf1a7a`): 칩 클릭 시 프론트는 `{ "preset": "care" }`만 보내고 `message`는 null이다. 이 null을 그대로 요청 바디에 넣으면 `Map.of`가 null 값을 거부해 NPE가 난다. 게다가 이 예외는 `subscribe()` 이전 동기 경로에서 터져 환불 콜백을 타지 않으므로 크레딧이 그대로 소멸했다. → 히스토리 저장에 쓰던 `resolveUserContent()`를 LLM 호출에도 사용하도록 통일.

## 4. Railway 배포 시 추가할 환경변수

Railway **Variables** 탭에 추가한다.

| 환경변수 | 필수 여부 | 비고 |
|---|---|---|
| `LLM_API_KEY` | **필수** | OpenAI API 키. 없으면 챗이 401로 죽는다(더미 폴백 없음). 코드/커밋에 절대 넣지 말 것 |
| `LLM_API_BASE_URL` | 선택 | 기본값 `https://api.openai.com/v1`, 다른 LLM으로 바꿀 때만 변경 |
| `LLM_API_MODEL` | 선택 | 기본값 `gpt-4o-mini` |

## 5. 요약

백엔드 하네스(인증·크레딧·컨텍스트·SSE)와 LLM 연동이 모두 끝났다. 남은 건 **Railway에 API 키 넣기**와 **AI 담당자의 프롬프트 문구 병합** 두 가지이고, 후자는 `GUARDRAIL_PROMPT` 상수 하나를 바꾸는 좁은 작업이다.
