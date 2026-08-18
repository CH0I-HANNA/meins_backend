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
- AI 담당자 레포가 실제 서버가 아니라면(프롬프트 실험 스크립트라면) **별도로 배포하지 않는다.** 크레딧/인증/컨텍스트 주입이 반드시 우리 백엔드에서 일어나야 하므로, 중간에 별도 서버를 끼우면 홉만 늘고 얻는 게 없다.
- **(갱신) 실제로는 AI 담당자가 자체 RAG 서버를 별도로 배포**해왔다 — `POST {base-url}/chat/stream`, body `{modelCode, message}`, SSE로 `{"type":"delta","text":"..."}` / `{"type":"done"}` 스트리밍. 위 원칙대로 프론트가 이 서버에 직접 붙지 않고, **우리 백엔드가 클라이언트로서 호출하는 구조**를 유지한다 — 크레딧 차감/오너 인증은 여전히 우리 쪽에서만 일어나고, `modelCode`도 프론트가 아니라 백엔드가 `tagCode`로 DB 조회해서 채운다.
- **가드레일 처리 주체가 바뀜**: AI 담당자가 공식 홈페이지 정보만으로 RAG DB를 구성하고, 거기서 못 찾는 정보·가격 관련 질문은 "알려드릴 수 없다, 공식 홈페이지 참고"로 답하도록 자체 프롬프트에서 처리했다고 확인함. 그래서 우리 쪽 `GUARDRAIL_PROMPT`/`buildSystemPrompt()`(가품 판정·가격·리셀 시세 금지 문구를 시스템 프롬프트로 강제 주입하던 코드)는 제거했다 — 새 API 계약(`modelCode` + `message`뿐)에는애초에 시스템 프롬프트를 실어 보낼 자리가 없다.

## 3. AI 파트 연동 상태 (완료)

- [x] `LlmWebClient.streamCompletion(modelCode, userMessage)` — AI 서버 `/chat/stream` 계약에 맞춰 재작성. `WebClient`가 `ServerSentEvent<String>`으로 SSE를 파싱하고, 각 청크 JSON의 `type`이 `delta`일 때만 `text`를 추출. `done`/알 수 없는 type은 빈 문자열로 필터링됨
- [x] `ChatHarnessService`가 `tagCode → modelCode`만 DB에서 조회해 넘기도록 변경 (`resolveModelCode()`). 제품 상세/가드레일 프롬프트 조립 로직은 AI 서버 쪽 책임이라 백엔드에서 제거
- [x] **스트림 종료 신호를 클라이언트까지 전달** — 스트림 끝에 이름 있는 이벤트를 한 번 보낸다(`event: done` 정상 완료 / `event: error` 서버가 인지한 실패: AI 서버 단절, 180초 타임아웃). 이걸 넣기 전에는 프론트가 정상 완료와 중간 끊김을 구분할 방법이 아예 없어 잘린 답변이 성공처럼 보였다. `data:`를 비워 보내므로 기존 파서(`data: ` 공백 포함으로 거르는 쪽)와 충돌하지 않는다 — 프론트가 대응하기 전에 배포해도 안전하다. 파싱 예시는 `FRONTEND_INTEGRATION.md` 2-5 참고
- [ ] OpenAI 대비 에러 응답 형식이 다를 수 있음 — AI 서버 쪽 에러(모델 미존재, 타임아웃 등)가 어떤 형태로 오는지 확인 필요. 구조적 제약은 동일: `SseEmitter` 반환 시점에 HTTP 200이 이미 커밋되므로 스트리밍 도중 에러는 `GlobalExceptionHandler`를 못 타고 스트림이 그냥 끊긴다
- [x] `SseEmitter` 타임아웃 재검토 — 실제로 RAG 응답이 60초를 넘는 질문에서 `AsyncRequestTimeoutException`으로 스트림이 강제 종료돼, 클라이언트엔 답변이 다 보이는데 `onComplete`(히스토리 저장)까지 못 가서 챗 히스토리에서 그 턴이 통째로 빠지는 버그로 실제 재현됨. `60_000L` → `180_000L`로 상향(`spring.mvc.async.request-timeout`도 동일하게 맞춤). 타임아웃 발생 시 `GlobalExceptionHandler`가 이미 커밋된 SSE 응답에 JSON을 쓰려다 2차 예외를 내던 것도 `AsyncRequestTimeoutException` 전용 핸들러(무응답)로 정리
- [ ] 크레딧 환불 정책 재확인 — 현재는 LLM 실패해도 환불 없음(스트림 종료/중단 양쪽에서 1턴 차감 보장 원칙대로 설계됨)
- [ ] AI 담당자 서버가 배포한 실제 환경(ngrok 무료 터널)으로 톤·가드레일 준수 여부 재현 테스트

## 4. 환경변수

| 환경변수 | 필수 여부 | 비고 |
|---|---|---|
| `AI_CHAT_BASE_URL` | **필수** | AI 담당자 RAG 서버 주소. **ngrok 무료 터널은 재시작마다 URL이 바뀌므로, 터널이 재시작되면 반드시 Railway Variables 값도 갱신해야 한다.** 장기적으로는 AI 담당자 쪽도 고정 도메인으로 배포하는 걸 권장 |

## 5. 요약

AI 담당자의 RAG 서버(`/chat/stream`)와 연동 완료. 백엔드 하네스(인증·크레딧·SSE)는 그대로 유지되고, 컨텍스트 조립 책임(제품 정보 조회·가드레일 프롬프트)은 AI 서버 쪽으로 넘어갔다 — 백엔드는 `tagCode → modelCode` 매핑만 담당한다.