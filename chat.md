# 챗 기능

AI 담당자가 프롬프트/모델을 아직 확정하지 못한 상태에서, 백엔드는 어떻게 계속 개발을 진행하고 나중에 무엇을 연결할지 정리한 문서.

## 1. 지금 상태: 하네스는 이미 완성, LLM만 자리표시자

`ChatHarnessService.streamChat()`이 처리하는 3계층 중 Layer 1(크레딧)·Layer 2(컨텍스트 조립)는 완성돼 있고, Layer 3(LLM 실행)만 더미다.

```
Layer 1 (가드레일)  CreditGuardService.checkCredit() / reserveCredit()   ✅ 완성
Layer 2 (컨텍스트)   ChatHarnessService.buildSystemPrompt()               ✅ 완성
Layer 3 (실행)       LlmWebClient.streamCompletion()                     ⏳ 더미 (150ms 간격 고정 텍스트)
```

즉 **AI 파트가 없어도 인증, 크레딧 차감, DB 컨텍스트 주입, SSE 스트리밍, 대화 히스토리 저장까지 전부 실제로 동작한다.** AI 담당자 작업물을 기다리지 않고도 프론트 연동·QA·데모 리허설이 가능한 이유가 이거다.

## 2. AI 파트가 나오기 전까지 우리가 할 일

- **더미로 계속 개발/테스트 진행.** `LlmWebClient.streamCompletion()`을 건드리지 않고 프론트 SSE 연동, abort 처리, 히스토리 조회, 크레딧 소진 흐름을 전부 검증한다.
- **통합 지점(interface)을 고정해둔다.** `streamCompletion(String systemPrompt, String userMessage) -> Flux<String>` 시그니처를 지금 확정해두면, AI 담당자가 어떤 모델/파라미터를 들고 오든 이 함수 내부만 교체하면 된다. 다른 계층(크레딧, 컨텍스트, SSE)은 손댈 필요 없음.
- **AI 담당자한테 코드가 아니라 3가지를 요청한다** (자세한 배경은 대화 로그 참고):
  1. 검증된 시스템 프롬프트 문구 (톤·가드레일 지시·few-shot 예시가 있다면 그것까지)
  2. 모델명 + 파라미터 (`model`, `temperature` 등)
  3. API 키 (Slack/코드로 공유 금지, Railway Variables에만)
- AI 담당자 레포가 실제 서버가 아니라면(프롬프트 실험 스크립트라면) **별도로 배포하지 않는다.** 크레딧/인증/컨텍스트 주입이 반드시 우리 백엔드에서 일어나야 하므로, 중간에 별도 서버를 끼우면 홉만 늘고 얻는 게 없다. → 우리 백엔드가 OpenAI를 직접 호출하는 구조로 간다.

## 3. AI 파트가 나오면 해야 할 작업 (체크리스트)

- [x] `LlmWebClient.streamCompletion()` 실제 구현으로 교체 — `WebClient`가 `ServerSentEvent<String>`으로 SSE를 직접 파싱하도록 구현(수동 줄 파싱 대신 Spring의 SSE 지원 사용), Jackson으로 각 청크의 `choices[0].delta.content`만 추출. `[DONE]`과 role-only/finish_reason-only 빈 청크는 필터링됨
- [ ] `ChatHarnessService.buildSystemPrompt()`의 `GUARDRAIL_PROMPT` / 제품 컨텍스트 / preset 컨텍스트에 AI 담당자가 검증한 프롬프트 문구 병합 — AI 담당자 결과물 도착 후 진행
- [x] 모델명 env var로 분리 — `llm.api.model` (`LLM_API_MODEL`, 기본값 `gpt-4o-mini`). `temperature`는 `LlmWebClient` 내부에 `0.7`로 하드코딩(자주 바꿀 값이 아니라 판단, 필요해지면 같은 방식으로 env var화)
- [ ] ~~OpenAI 에러를 `BusinessException(ErrorCode)`로 매핑~~ → **구조적으로 불가능해서 방향 수정 필요**: 컨트롤러가 `SseEmitter`를 반환하는 순간 HTTP 200 + `text/event-stream` 헤더가 이미 커밋되기 때문에, 스트리밍 도중 발생하는 OpenAI 에러(401/429/5xx)는 `GlobalExceptionHandler`를 못 타고 `{code,message,traceId}` JSON 바디로 못 내려간다. 지금은 기존 `error -> emitter.completeWithError(error)`가 스트림을 끊는 걸로 그대로 둠 — 프론트가 이 경우(빈 응답으로 스트림 종료)를 어떻게 보여줄지는 별도 논의 필요
- [ ] `SseEmitter(60_000L)` 타임아웃이 실제 응답 속도에 맞는지 재검토
- [ ] 크레딧 환불 정책 재확인 — 현재는 LLM 실패해도 환불 없음(스트림 종료/중단 양쪽에서 1턴 차감 보장 원칙대로 설계됨). 실제 정책과 맞는지 확인
- [ ] AI 담당자가 검증했던 질문/시나리오를 실제 API로 재현해 톤·가드레일 준수 여부 확인

## 4. Railway 배포 시 추가할 환경변수

`application.properties`에 이미 배선돼 있어 지금은 안 넣어도 앱이 죽지 않음(빈 값이면 더미 응답만 계속 동작). 실제 연동 시점에 Railway **Variables** 탭에 추가:

| 환경변수 | 필수 여부 | 비고 |
|---|---|---|
| `LLM_API_KEY` | **필수** | OpenAI API 키. 코드/커밋에 절대 넣지 말 것 |
| `LLM_API_BASE_URL` | 선택 | 기본값 `https://api.openai.com/v1`, 다른 LLM으로 바꿀 때만 변경 |
| `LLM_API_MODEL` | 선택 | 기본값 `gpt-4o-mini` |

## 5. 요약

AI 파트 완성 여부와 무관하게 백엔드 하네스(인증·크레딧·컨텍스트·SSE)는 이미 끝나 있다. 남은 건 `LlmWebClient` 한 파일 교체 + 프롬프트 문구 병합 + 에러 매핑 + Railway 환경변수 추가뿐이라, AI 담당자 작업물이 나오는 즉시 좁은 범위로 붙일 수 있다.