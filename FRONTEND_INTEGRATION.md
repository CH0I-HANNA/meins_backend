# 프론트엔드 연동 명세

MCM Onboarding Backend API를 프론트엔드에서 연동하기 위한 명세다. 실제 코드 기준으로 작성했으며, Swagger UI(`/swagger-ui.html`)에서 "Try it out"으로 직접 호출하며 대조할 수 있다.

## 0. 기본 정보

- Base URL: `http://localhost:8080` (배포 도메인 확정 시 교체)
- 모든 요청/응답 Content-Type: `application/json` (챗 스트리밍 응답만 `text/event-stream`)
- 인증 방식 2가지:
  - **오너 토큰**: `Authorization: Bearer mcm:own:{tagCode}:{authCode}` — 소유권 등록 후 발급됨
  - **관리자 키**: `X-Admin-Key: {ADMIN_KEY}` — `/admin/**`에만 사용 (관리자 도구 전용, 일반 사용자 플로우에선 안 씀)

## 0-1. 코드 포맷

| 필드 | 형식 | 비고 |
|---|---|---|
| 태그 코드 (tagCode) | `XXXX-XXXX` | 영문 + 숫자, **대소문자 무시** |
| 인증 코드 (authCode) | `XXXX-XXXX-XXXX` | 영문 + 숫자, `0`/`O`/`1`/`I` 미사용(입력 실수 방지), **대소문자 무시** |

**대소문자 무시**란 서버가 모든 입력(URL의 tagCode, body의 authCode, 토큰)을 대문자로 정규화해서 비교한다는 뜻이다. 즉 프론트는 사용자가 소문자로 입력해도 그대로 보내면 되고, 별도로 대문자 변환을 할 필요는 없다(해도 무방).

## 1. 공통 에러 응답

모든 에러는 아래 포맷으로 온다. HTTP status code로 1차 분기하고, `code`로 세부 처리하면 된다.

```json
{
  "code": "TAG_001",
  "message": "존재하지 않는 태그입니다.",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| code | HTTP | 의미 |
|---|---|---|
| `AUTH_001` | 401 | 토큰 없음/형식 오류/authCode 불일치/미등록 태그 접근 |
| `AUTH_002` | 401 | 토큰의 tagCode와 URL의 tagCode 불일치 |
| `AUTH_003` | 401 | 관리자 키 불일치 (관리자 도구 전용) |
| `CREDIT_001` | 429 | 챗 크레딧 소진 |
| `OWN_001` | 403 | 등록 시도 5회 초과 잠금 |
| `OWN_002` | 409 | 이미 등록된 태그에 재등록 시도 |
| `OWN_003` | 401 | 인증 코드(authCode) 불일치 |
| `TAG_001` | 404 | 존재하지 않는 tagCode |
| `ADMIN_001` | 400 | 잘못된 force-status action (관리자 도구 전용) |
| `SERVER_001` | 500 | 서버 내부 오류 |

## 2. 사용자 플로우 (QR 스캔 → 등록 → 챗)

### 2-1. QR 스캔 직후 — 제품 정보 + 등록 상태 조회

```
GET /api/tags/{tagCode}
```
인증 불필요. QR에는 tagCode 원문이 그대로 인코딩되어 있다 (예: `AB3D-9F2K`).

**응답 200**
```json
{
  "tagCode": "AB3D-9F2K",
  "productName": "MCM 클래식 백팩",
  "manufacturedYm": "2026-08",
  "material": "비세토스 캔버스",
  "color": "브라운",
  "saleRegisteredYm": "2026-09",
  "widthCm": 30,
  "depthCm": 15,
  "heightCm": 20,
  "status": "UNREGISTERED",
  "registeredAt": null
}
```

**프론트 분기 기준은 `status`**:
- `"REGISTERED"` → "이미 등록된 제품입니다" 안내 화면 (인증코드 입력 폼 노출 안 함)
- `"UNREGISTERED"` → 제품 정보 + 인증코드 입력 폼 노출

**`registeredAt`은 이 엔드포인트(게스트)에서는 `"YYYY-MM"` 정밀도로만 온다** (미등록이면 `null`). 분단위까지는 오너만 볼 수 있다 — 2-3 참고. 이 마스킹은 서버가 처리하므로 프론트는 받은 문자열을 그대로 표시하면 된다.

`widthCm`/`depthCm`/`heightCm`(가로/세로/높이, cm 단위 정수)은 값이 없으면 `null`로 온다.

에러: 존재하지 않는 tagCode → 404 `TAG_001` (QR 자체가 잘못됐거나 위조된 경우).

### 2-2. 소유권 등록 (인증코드 입력)

```
POST /api/tags/{tagCode}/ownership
Content-Type: application/json

{ "authCode": "7K2P-9QXT-4M8W" }
```
`authCode`는 실물 제품에 인쇄된 값이며 사용자가 직접 입력한다. 인증 헤더 불필요.

**응답 200**
```json
{
  "tagCode": "AB3D-9F2K",
  "token": "mcm:own:AB3D-9F2K:7K2P-9QXT-4M8W",
  "initialCredits": 30
}
```
이 `token`을 클라이언트에 저장(localStorage 등)해서 이후 모든 오너 API 호출에 `Authorization: Bearer {token}` 헤더로 사용한다.

**에러 처리**
- `OWN_003` (401) — 인증코드 오타. "인증 코드를 다시 확인해주세요" 정도로 안내하고 재입력 받으면 됨.
- `OWN_002` (409) — 이미 등록된 태그. UI 로직상 2-1에서 걸러지므로 정상 플로우에선 거의 안 나오지만, 두 기기에서 동시에 등록 시도하는 등 레이스 상황에서는 발생 가능.
- `OWN_001` (403) — 5회 실패로 잠금. "고객센터로 문의해주세요" 안내 (관리자가 `UNLOCK`/`UNLOCK_RECOVERY`로 풀어줘야 함).
- `TAG_001` (404) — tagCode 자체가 없음.

> 인증코드 오답/이미등록 재시도가 **같은 실패 카운트**를 공유해서 5회째 잠긴다. 입력 폼에서 "n회 더 틀리면 잠깁니다" 같은 카운트는 서버가 안 내려주니 별도로 보여줄 수 없다 — 원하면 백엔드에 잔여 시도 횟수 필드 추가를 요청해야 함.

### 2-3. 오너 홈 조회

```
GET /api/tags/{tagCode}/home
Authorization: Bearer {token}
```
응답 포맷은 2-1과 동일한 `TagDetailResponse`. 등록 후 대시보드/홈 화면에서 사용.

**여기서는 `registeredAt`이 `"YYYY-MM-DD HH:mm"` 전체 정밀도로 온다** (예: `"2026-08-06 20:00""`) — 소유 등록 시점을 정확히 보여줄 수 있는 화면은 오너 홈뿐이다.

에러: 토큰 없음/불일치 → 401 `AUTH_001`, tagCode 불일치(다른 태그의 토큰으로 호출) → 401 `AUTH_002`.

### 2-4. AI 챗 — SSE 스트리밍

```
POST /api/tags/{tagCode}/chat
Authorization: Bearer {token}
Content-Type: application/json

{ "preset": "care" }     // care | style | heritage 중 하나
```
또는
```json
{ "message": "이 가방 어떻게 관리하나요?" }
```
`message`와 `preset`은 둘 중 하나만 보낸다.

**⚠️ 중요 — 브라우저 `EventSource`로 못 씀**: 이 엔드포인트는 `POST` + `Authorization` 헤더가 필요한데, 브라우저 네이티브 `EventSource` API는 `GET`만 지원하고 커스텀 헤더도 못 보낸다. 아래처럼 `fetch` + `ReadableStream`으로 직접 SSE를 파싱해야 한다.

```javascript
const response = await fetch(`/api/tags/${tagCode}/chat`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ message: '이 가방 어떻게 관리하나요?' }),
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  const chunk = decoder.decode(value);
  // SSE 포맷: "data: {청크}\n\n" 라인들을 파싱해서 그때그때 화면에 append
  const lines = chunk.split('\n').filter(l => l.startsWith('data:'));
  for (const line of lines) {
    const text = line.replace(/^data:\s*/, '');
    // text를 채팅 말풍선에 append
  }
}
```

**응답 코드**
- `200` + `text/event-stream` — 정상 스트리밍 시작
- `429` `CREDIT_001` — 크레딧 소진. "AI 상담 횟수를 모두 사용했습니다" 안내.
- `401` — 토큰 문제 (2-3과 동일)

**참고**: 서버는 현재 실제 LLM 대신 더미 텍스트를 150ms 간격으로 스트리밍한다(`LlmWebClient` 자리표시자). 실제 응답 품질은 LLM 연동 이후 확인 가능.

### 2-5. 챗 히스토리 조회

```
GET /api/tags/{tagCode}/chat/history
Authorization: Bearer {token}
```
```json
[
  { "id": 1, "tagCode": "AB3D-9F2K", "role": "user", "content": "...", "preset": null, "createdAt": "2026-08-06T20:00:00" }
]
```

> **알려진 제약**: 현재 백엔드에 챗 메시지를 실제로 저장하는 로직이 아직 연결되어 있지 않아, 이 엔드포인트는 **항상 빈 배열 `[]`을 반환한다**. 프론트에서 대화 내역 화면을 만들 계획이라면 이 부분 백엔드 작업이 선행되어야 한다 — 필요하면 알려달라.

## 3. 관리자 도구 (별도 화면/툴인 경우)

일반 사용자 플로우와는 분리된, 내부 운영자용 화면이다. 모든 요청에 `X-Admin-Key` 헤더 필요.

| 기능 | 메서드/경로 |
|---|---|
| 제품 등록 + QR 일괄 발급 | `POST /admin/tags/bulk-create` |
| 태그 목록 조회 | `GET /admin/tags` |
| QR 이미지 단건 (PNG) | `GET /admin/qr/{tagCode}` |
| QR 이미지 전체 (zip) | `GET /admin/tags/qr-export` |
| 상태 강제 변경 (잠금해제/데모) | `POST /admin/tags/{tagCode}/force-status` |

`POST /admin/tags/bulk-create` request body 예시:
```json
{
  "productName": "MCM 클래식 백팩",
  "manufacturedYm": "2026-08",
  "material": "비세토스 캔버스",
  "color": "브라운",
  "saleRegisteredYm": "2026-09",
  "widthCm": 30,
  "depthCm": 15,
  "heightCm": 20,
  "quantity": 50
}
```

상세 요청/응답 스키마는 Swagger UI의 **Admin** 그룹에서 바로 확인 가능 (필드가 많아 이 문서에서는 생략).

## 4. 상태 값 요약

`TagDetailResponse.status`, 태그의 등록 상태를 나타내는 유일한 값이다.

| 값 | 의미 |
|---|---|
| `UNREGISTERED` | 아직 아무도 등록 안 함 → 인증코드 입력 폼 노출 |
| `REGISTERED` | 이미 등록됨 → 안내 메시지만 노출, 등록 폼 숨김 |
