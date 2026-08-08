# 프론트엔드 연동 명세

노션 `중앙해커톤 / API` 기획 명세를 기준으로 구현된 실제 코드 기준 문서다. Swagger UI(`/swagger-ui.html`)에서 "Try it out"으로 직접 호출하며 대조할 수 있다.

## 0. 기본 정보

- Base URL: `http://localhost:8080` (배포 도메인 확정 시 교체)
- 모든 요청/응답 Content-Type: `application/json` (챗 스트리밍 응답만 `text/event-stream`)
- 시간 기준: **KST**. 게스트에게 나가는 시각은 `YYYY-MM`, 오너 응답은 ISO 8601 오프셋(`2026-03-14T09:22:00+09:00`)
- 인증 방식 2가지:
  - **오너 토큰**: `Authorization: Bearer mcm:own:{tagCode}:{authCode}` — 소유권 등록 후 발급. 태그 1개에만 묶이므로 `localStorage`에 태그별로 분리 저장(`mcm:own:{tagCode}`)
  - **관리자 키**: `X-Admin-Key: {ADMIN_KEY}` — `/admin/**`에만 사용 (내부 운영 도구 전용)

### 화면 1개 = 호출 1개

| 화면 | 호출 |
|---|---|
| 01 조회 → 02 게스트 뷰 | `GET /api/tags/{tagCode}` 1회 |
| 03 코드 입력 | `POST /api/tags/{tagCode}/ownership` 1회 |
| 04 오너 홈 | `GET /api/tags/{tagCode}/ownership/me` 1회 |
| 05 소유권 | 추가 호출 없음 (04 응답 재사용) |
| 06 챗 | `GET .../chat/history` 1회 + 발화당 `POST .../chat` |

## 0-1. 코드 포맷

| 필드 | 형식 | 비고 |
|---|---|---|
| 태그 코드 (tagCode) | `XXXX-XXXX` | 영문 + 숫자, **대소문자 무시**. 형식이 어긋나면 400 `TAG_INVALID_FORMAT` |
| 인증 코드 (code) | `XXXXXXXXXXXX` (12자) | `0`/`O`/`1`/`I` 미사용, **대소문자 무시**, **하이픈 있어도 무시** |

서버가 모든 입력(URL의 tagCode, body의 code, 토큰)을 대문자로 정규화하고 인증 코드의 하이픈을 제거한 뒤 비교한다. 프론트는 사용자 입력을 그대로 보내도 되고, 명세대로 하이픈 제거 + 대문자 변환 후 보내도 된다.

## 1. 공통 에러 응답

```json
{
  "code": "TAG_NOT_FOUND",
  "message": "태그를 확인할 수 없습니다.",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`traceId`는 모든 에러에 포함된다. **토스트에는 표시하지 말고 `console.error`로만 남길 것.**

| code | HTTP | 의미 | 프론트 처리 |
|---|---|---|---|
| `TAG_NOT_FOUND` | 404 | 존재하지 않는 태그 | 태그 확인 불가 화면 |
| `TAG_INVALID_FORMAT` | 400 | 태그 코드 형식 오류 | 태그 확인 불가 화면 |
| `CODE_MISMATCH` | 400 | 인증 코드 불일치 | 오류 표시 + 남은 횟수 |
| `CODE_LOCKED` | 429 | 시도 초과 잠금 | 잠금 화면 + 카운트다운 |
| `ALREADY_REGISTERED` | 409 | 이미 등록된 태그 | 게스트 뷰(등록)로 이동 |
| `TOKEN_INVALID` | 401 | 토큰 무효·만료·다른 태그의 토큰 | 로컬 토큰 삭제 후 게스트 뷰 |
| `CREDIT_EXHAUSTED` | 429 | 챗 크레딧 소진 | 하드코딩 문구 |
| `ADMIN_KEY_INVALID` | 401 | 관리자 키 불일치 (내부 도구 전용) | — |
| `ADMIN_INVALID_ACTION` | 400 | 잘못된 force-status action (내부 도구 전용) | — |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 | 토스트 |

`TAG_NOT_FOUND` / `TAG_INVALID_FORMAT`은 **모두 같은 화면**으로 처리한다(원인 특정과 무관). 열거 방지를 위해 message도 동일하다.

### 에러별 부가 필드

해당 에러에만 실리며, 없으면 키 자체가 응답에서 빠진다.

```json
{ "code": "CODE_MISMATCH", "message": "...", "traceId": "...", "remainingAttempts": 4 }
{ "code": "CODE_LOCKED",   "message": "...", "traceId": "...", "lockedUntil": "2026-03-15T09:22:00+09:00" }
```

프론트는 **자체 실패 카운터를 두지 말고** `remainingAttempts` / `lockedUntil`만 표시한다(세션 기준 카운터는 시크릿 창으로 우회 가능하므로 서버가 판정한다).

> `CREDIT_EXHAUSTED`에도 `resetAt` 필드 자리가 있지만, 크레딧 회복 정책이 확정되지 않아 **현재는 항상 생략된다**.

## 2. 사용자 플로우

### 2-1. 태그 조회 (01 → 02)

```
GET /api/tags/{tagCode}
```
인증 불필요.

**응답 200**
```json
{
  "tagCode": "A1B2-C3D4",
  "product": {
    "name": "MCM 클래식 백팩",
    "modelCode": "MMK-AA1234",
    "heroImage": "https://.../hero.jpg",
    "detailImages": ["https://.../1.jpg", "https://.../2.jpg"],
    "material": "코티드 캔버스",
    "size": { "width": 30, "depth": 12, "height": 22 },
    "color": "코냑",
    "productUrl": "https://..."
  },
  "official": {
    "manufacturedAt": "2025-11",
    "releasedAt": "2026-01"
  },
  "ownership": {
    "registered": true,
    "registeredAt": "2026-03"
  }
}
```

- `detailImages`: **0~3개**. 프론트가 개수에 맞춰 렌더한다.
- `material` / `size` / `color` / `productUrl`: 값이 없으면 **키 자체가 빠진다** → 해당 행/버튼 숨김 처리.
- `ownership.registered`로 하단 버튼 상태를 전환한다(등록됨이면 잠금).
- `ownership.registeredAt`: 미등록이면 `null`, 등록됐으면 `YYYY-MM`. **게스트에게는 분 단위가 아예 응답에 담기지 않는다**(서버가 잘라서 내리므로 API를 직접 호출해도 우회 불가).
- 구매처(`purchasedFrom`)는 8/2 회의 결정에 따라 **어디에도 포함되지 않는다**.

### 2-2. 소유권 등록 (03)

```
POST /api/tags/{tagCode}/ownership
Content-Type: application/json

{ "code": "F26T59QR9D3K" }
```
인증 불필요.

**응답 200**
```json
{
  "token": "mcm:own:A1B2-C3D4:F26T59QR9D3K",
  "record": { "registeredAt": "2026-03-14T09:22:00+09:00" }
}
```
`token`을 `localStorage`에 태그별로 저장(`mcm:own:{tagCode}`)해서 이후 오너 API 호출에 `Authorization: Bearer {token}`으로 사용한다.

**에러 처리**
- `CODE_MISMATCH` (400) + `remainingAttempts` — 세 입력 필드를 동일하게 오류 표시하고 남은 횟수 노출.
- `CODE_LOCKED` (429) + `lockedUntil` — 잠금 화면. `lockedUntil`로 "N시간 M분 후 다시 시도" 계산.
- `ALREADY_REGISTERED` (409) — 게스트 뷰(등록)로 이동.
- `TAG_NOT_FOUND` (404) / `TAG_INVALID_FORMAT` (400) — 태그 확인 불가 화면.

> **잠금 정책**: 실패 누적과 잠금 판정은 서버가 `tagCode + ip_hash` 조합으로 관리한다. 5회 실패 시 24시간 잠금되고, 시각이 지나면 자동 해제된다. 이미 사용된 코드는 `CODE_MISMATCH`와 동일하게 처리되어 사용 여부가 노출되지 않는다.

### 2-3. 토큰 검증 / 소유 레코드 조회 (04)

```
GET /api/tags/{tagCode}/ownership/me
Authorization: Bearer {token}
```

**응답 200**
```json
{
  "record": { "registeredAt": "2026-03-14T09:22:00+09:00" },
  "product": { "...": "2-1과 동일 구조" },
  "official": { "...": "2-1과 동일 구조" }
}
```
오너 홈은 게스트 뷰와 같은 정보 + 등록 정보 카드이므로 `product`/`official`을 함께 내려 **호출 1회**로 끝난다. 05 소유권 화면은 이 응답을 재사용하며 추가 호출이 없다.

`record.registeredAt`은 ISO 8601(KST 오프셋) 전체 정밀도 → 프론트가 `YYYY-MM-DD HH:mm`으로 포맷한다.

에러: `TOKEN_INVALID` (401) — 토큰 없음/형식 오류/다른 태그의 토큰/미등록 태그가 전부 여기에 해당한다. 로컬 토큰을 삭제하고 게스트 뷰로 전환.

### 2-4. 챗 히스토리 조회 (06 진입)

```
GET /api/tags/{tagCode}/chat/history
Authorization: Bearer {token}
```

**응답 200**
```json
{
  "messages": [
    { "role": "assistant", "content": "...", "createdAt": "2026-03-14T09:22:00+09:00" },
    { "role": "user", "content": "...", "createdAt": "2026-03-14T09:23:00+09:00" }
  ],
  "credits": { "remaining": 7, "limit": 30 }
}
```
`credits.remaining`이 2 이하일 때 프론트가 안내 문구를 띄운다.

`messages`는 서버에 저장된 실제 대화 내역이다(`tagCode` 기준). 재진입 시에도 이전 대화가 그대로 복원된다.

### 2-5. AI 챗 — SSE 스트리밍 (06)

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
`message`와 `preset`은 둘 중 하나만 보낸다. **컨텍스트는 서버가 토큰으로 소유 레코드를 조회해 직접 주입**하므로 프론트는 메시지 또는 preset 값만 보낸다.

**⚠️ 브라우저 `EventSource`로 못 씀**: `POST` + `Authorization` 헤더가 필요한데 네이티브 `EventSource`는 `GET`만 지원하고 커스텀 헤더를 못 보낸다. `fetch` + `ReadableStream`으로 직접 SSE를 파싱해야 한다.

```javascript
const controller = new AbortController();
const response = await fetch(`/api/tags/${tagCode}/chat`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ message: '이 가방 어떻게 관리하나요?' }),
  signal: controller.signal,   // 중단 버튼에서 controller.abort()
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  const chunk = decoder.decode(value);
  const lines = chunk.split('\n').filter(l => l.startsWith('data:'));
  for (const line of lines) {
    const text = line.replace(/^data:\s*/, '');
    // text를 채팅 말풍선에 append
  }
}
```

- 중단(`abort`) 시 서버도 LLM 생성을 즉시 취소한다.
- **크레딧 차감은 중단 여부와 무관하게 1턴 고정**이며, 스트림 종료 시점에 서버가 차감한다.
- 크레딧 소진 시 LLM을 호출하지 않고 `429 CREDIT_EXHAUSTED`로 응답한다 → 안내 문구는 프론트에서 하드코딩("오늘 나눌 수 있는 대화는 여기까지입니다.").

**응답 코드**: `200` + `text/event-stream` / `429` `CREDIT_EXHAUSTED` / `401` `TOKEN_INVALID`

> **참고 (미해결)**: 서버는 현재 실제 LLM 대신 더미 텍스트를 150ms 간격으로 스트리밍한다(`LlmWebClient` 자리표시자).

## 3. 관리자 도구

내부 운영자용. 모든 요청에 `X-Admin-Key` 헤더 필요. 일반 사용자 플로우와 무관하다.

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
  "modelCode": "MMK-AA1234",
  "manufacturedYm": "2025-11",
  "material": "코티드 캔버스",
  "color": "코냑",
  "saleRegisteredYm": "2026-01",
  "widthCm": 30,
  "depthCm": 12,
  "heightCm": 22,
  "quantity": 50
}
```

상세 스키마는 Swagger UI의 **Admin** 그룹 참고.

## 4. 아직 구현되지 않은 것 (기획 명세 대비)

프론트에서 화면을 만들기 전에 백엔드 작업이 선행되어야 하는 항목:

- **소유권 카드 이미지** `GET /api/tags/{tagCode}/ownership/card.png` — 미구현
- **OG 태그 제어** (공유 링크 미리보기) — 미구현
- **소유권 코드 재발급** `POST .../ownership/reissue` — MVP 범위 밖(설계만)
- **IP 시간당 상한** (`RATE_LIMITED`) — 미구현
- **크레딧 자동 회복(롤링 리셋)** — 미구현. 소진 시 관리자 `force-status`로만 복구 가능하며 `resetAt`도 내려가지 않는다
- **실제 LLM 연동** — 미구현 (2-5 참고, 더미 응답만 스트리밍됨)
- **CORS 설정** — 미설정. 프론트가 다른 도메인에서 호출하려면 백엔드 설정이 필요하다
