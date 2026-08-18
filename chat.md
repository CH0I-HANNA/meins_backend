# 챗 기능

챗 기능의 설계 배경과 AI 파트 연동 이력을 정리한 문서. 3절이 현재 상태, 1~2절은 연동 전 계획(이력)이다.

## 1. 현재 상태: 3계층 전부 실제 동작 (연동 완료)

`ChatHarnessService.streamChat()`이 처리하는 3계층 모두 실제 구현이다 — 더미 스트리밍은 남아 있지 않다.

```
Layer 1 (가드레일)  CreditGuardService.checkCredit() / reserveCredit()   ✅ 완성
Layer 2 (컨텍스트)   ChatHarnessService.resolveModelCode()                ✅ 완성 (tagCode → modelCode)
Layer 3 (실행)       LlmWebClient.streamCompletion(modelCode, message)    ✅ AI 서버 /chat/stream 호출
```

인증, 크레딧 차감/환불, 컨텍스트(modelCode) 주입, SSE 스트리밍, 대화 히스토리 저장까지 전부 실제로 동작한다. Layer 2가 원래는 시스템 프롬프트 조립(`buildSystemPrompt()`)이었으나, 가드레일·제품 컨텍스트 책임이 AI 서버로 넘어가면서 `tagCode → modelCode` 매핑만 남았다(2절 마지막 두 항목 참고).

## 2. (이력) AI 파트가 나오기 전까지 세웠던 계획

> 아래는 AI 담당자 결과물을 기다리는 동안의 계획이고, 실제로 어떻게 귀결됐는지는 이 절 마지막 두 항목과 3절에 있다.

- **더미로 계속 개발/테스트 진행.** `LlmWebClient.streamCompletion()`을 건드리지 않고 프론트 SSE 연동, abort 처리, 히스토리 조회, 크레딧 소진 흐름을 전부 검증한다. → 실제로 이 전략이 통했다: 연동 시 교체된 건 `LlmWebClient` 내부와 인자 하나(`systemPrompt` → `modelCode`)뿐이다.
- **통합 지점(interface)을 고정해둔다.** `streamCompletion(String systemPrompt, String userMessage) -> Flux<String>` 시그니처를 지금 확정해두면, AI 담당자가 어떤 모델/파라미터를 들고 오든 이 함수 내부만 교체하면 된다. 다른 계층(크레딧, 컨텍스트, SSE)은 손댈 필요 없음.
- **AI 담당자한테 코드가 아니라 3가지를 요청한다** (자세한 배경은 대화 로그 참고):
  1. 검증된 시스템 프롬프트 문구 (톤·가드레일 지시·few-shot 예시가 있다면 그것까지)
  2. 모델명 + 파라미터 (`model`, `temperature` 등)
  3. API 키 (Slack/코드로 공유 금지, Railway Variables에만)
- AI 담당자 레포가 실제 서버가 아니라면(프롬프트 실험 스크립트라면) **별도로 배포하지 않는다.** 크레딧/인증/컨텍스트 주입이 반드시 우리 백엔드에서 일어나야 하므로, 중간에 별도 서버를 끼우면 홉만 늘고 얻는 게 없다.
- **(갱신) 실제로는 AI 담당자가 자체 RAG 서버를 별도로 배포**해왔다 — `POST {base-url}/chat/stream`, body `{modelCode, message}`, SSE로 `{"type":"delta","text":"..."}` / `{"type":"done"}` 스트리밍. 위 원칙대로 프론트가 이 서버에 직접 붙지 않고, **우리 백엔드가 클라이언트로서 호출하는 구조**를 유지한다 — 크레딧 차감/오너 인증은 여전히 우리 쪽에서만 일어나고, `modelCode`도 프론트가 아니라 백엔드가 `tagCode`로 DB 조회해서 채운다.
- **가드레일 처리 주체가 바뀜**: AI 담당자가 공식 홈페이지 정보만으로 RAG DB를 구성하고, 거기서 못 찾는 정보·가격 관련 질문은 "알려드릴 수 없다, 공식 홈페이지 참고"로 답하도록 자체 프롬프트에서 처리했다고 확인함. 그래서 우리 쪽 `GUARDRAIL_PROMPT`/`buildSystemPrompt()`(가품 판정·가격·리셀 시세 금지 문구를 시스템 프롬프트로 강제 주입하던 코드)는 제거했다 — 새 API 계약(`modelCode` + `message`뿐)에는애초에 시스템 프롬프트를 실어 보낼 자리가 없다.

## 3. AI 파트 연동 상태 (완료)

- [x] `LlmWebClient.streamCompletion(modelCode, userMessage)` — AI 서버 `/chat/stream` 계약에 맞춰 재작성. `WebClient`가 `ServerSentEvent<String>`으로 SSE를 파싱하고, 각 청크 JSON의 `type`이 `delta`일 때만 `text`를 추출. `done`/알 수 없는 type은 빈 문자열로 필터링됨
- [x] `ChatHarnessService`가 `tagCode → modelCode`만 DB에서 조회해 넘기도록 변경 (`resolveModelCode()`). 제품 상세/가드레일 프롬프트 조립 로직은 AI 서버 쪽 책임이라 백엔드에서 제거
- [ ] OpenAI 대비 에러 응답 형식이 다를 수 있음 — AI 서버 쪽 에러(모델 미존재, 타임아웃 등)가 어떤 형태로 오는지 확인 필요. 구조적 제약은 동일: `SseEmitter` 반환 시점에 HTTP 200 + `text/event-stream` 헤더가 이미 커밋되므로, 스트리밍 도중 에러는 `GlobalExceptionHandler`를 못 타고 `{code,message,traceId}` 바디로 내려갈 수 없다 — 클라이언트에는 **비정상 종료**로만 나타난다. 이 "비정상 종료"는 청크를 하나도 못 보낸 상태(빈 응답)에 한정되지 않는다: `emitter.completeWithError()`는 이미 일부 delta를 중계한 뒤에도 호출될 수 있고(AI 서버/ngrok 터널의 네트워크 단절, `done` 청크 없이 연결 종료, `SseEmitter` 180초 타임아웃), 그 경우 프론트는 **잘린 응답을 받는다**
- [ ] **스트림 종료 신호를 클라이언트까지 전달** — 현재 백엔드는 AI 서버의 `{"type":"done"}`을 완료 신호로 쓰고 소비해버리고, 클라이언트 쪽으로는 `data:` 청크만 보낸 뒤 emitter를 닫는다. 그래서 프론트는 **정상 완료와 중간 끊김을 구분할 수 없다** — 잘린 응답이 조용히 성공처럼 보인다. 종료용 이벤트(예: `event: done` / `event: error`)를 명시적으로 한 번 내려주면 프론트가 실패를 판정할 수 있다. 프론트가 지금 쓸 수 있는 최소 휴리스틱(첫 `data:` 수신 전 종료 = 실패)은 `FRONTEND_INTEGRATION.md` 2-5에 적어둠
- [x] `SseEmitter` 타임아웃 재검토 — 실제로 RAG 응답이 60초를 넘는 질문에서 `AsyncRequestTimeoutException`으로 스트림이 강제 종료돼, 클라이언트엔 답변이 다 보이는데 `onComplete`(히스토리 저장)까지 못 가서 챗 히스토리에서 그 턴이 통째로 빠지는 버그로 실제 재현됨. `60_000L` → `180_000L`로 상향(`spring.mvc.async.request-timeout`도 동일하게 맞춤). 타임아웃 발생 시 `GlobalExceptionHandler`가 이미 커밋된 SSE 응답에 JSON을 쓰려다 2차 예외를 내던 것도 `AsyncRequestTimeoutException` 전용 핸들러(무응답)로 정리
- [x] 크레딧 환불 정책 확정 — 기획 명세 2-5 "호출 실패 시 미차감"에 맞춰, LLM 호출 직전 1턴을 선차감하고 **스트림이 에러로 끝나는 `onError` 경로에서만** `refundCredit()`으로 되돌린다(PR #3). 정상 완료·클라이언트 중단(abort)은 1턴 차감 유지. 환불 상한은 태그별 실제 `limit`을 쓴다(30 하드코딩 버그는 PR #10에서 수정)
- [ ] AI 담당자 서버가 배포한 실제 환경(ngrok 무료 터널)으로 톤·가드레일 준수 여부 재현 테스트

### 3-1. 연동 과정에서 잡은 실행 시점 이슈 2건

둘 다 컴파일은 통과하지만 실행하면 터지는 종류라 기록해둔다. (OpenAI 직접 호출 시절 PR #4에서 잡았지만, 원인이 프레임워크/계약 쪽이라 AI 서버 프록시 구조에도 그대로 해당된다.)

- **Jackson 2 → Jackson 3** (`f9facdf`): Spring Boot 4.1은 Jackson 3(`tools.jackson`)만 자동 설정한다. `JacksonAutoConfiguration`이 등록하는 빈은 `tools.jackson.databind.json.JsonMapper` 하나뿐이라, `com.fasterxml.jackson.databind.ObjectMapper`를 주입받으면 기동 시 `NoSuchBeanDefinitionException`으로 앱이 뜨지 않는다. Jackson 2 자체는 springdoc 등이 끌고 와 클래스패스에 있어서 **컴파일은 통과한다** — 새 라이브러리를 붙일 때 주의할 지점. 현재 `LlmWebClient`도 `tools.jackson.databind.ObjectMapper`를 쓴다.
- **preset 전용 요청 NPE** (`1bf1a7a`): 칩 클릭 시 프론트는 `{ "preset": "care" }`만 보내고 `message`는 null이다. 이 null을 그대로 요청 바디에 넣으면 `Map.of`가 null 값을 거부해 NPE가 난다. 게다가 이 예외는 `subscribe()` 이전 동기 경로에서 터져 환불 콜백을 타지 않으므로 크레딧이 그대로 소멸했다. → 히스토리 저장에 쓰던 `resolveUserContent()`를 LLM 호출에도 사용하도록 통일.

## 4. 환경변수

| 환경변수 | 필수 여부 | 비고 |
|---|---|---|
| `AI_CHAT_BASE_URL` | **필수** | AI 담당자 RAG 서버 주소. **ngrok 무료 터널은 재시작마다 URL이 바뀌므로, 터널이 재시작되면 반드시 Railway Variables 값도 갱신해야 한다.** 장기적으로는 AI 담당자 쪽도 고정 도메인으로 배포하는 걸 권장 |

## 5. 요약

AI 담당자의 RAG 서버(`/chat/stream`)와 연동 완료. 백엔드 하네스(인증·크레딧·SSE)는 그대로 유지되고, 컨텍스트 조립 책임(제품 정보 조회·가드레일 프롬프트)은 AI 서버 쪽으로 넘어갔다 — 백엔드는 `tagCode → modelCode` 매핑만 담당한다.