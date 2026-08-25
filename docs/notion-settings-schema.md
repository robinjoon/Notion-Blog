# Notion Blog Settings Data Source

`NOTION_SETTINGS_DATA_SOURCE_ID`는 블로그의 소스 설정을 저장하는 비공개 Notion data source ID다. 이 data source는 공개하지 않고 Notion integration에만 공유한다.

애플리케이션은 고정 API 버전 `2026-03-11`로 다음 endpoint를 cursor 끝까지 조회한다.

```text
POST /v1/data_sources/{dataSourceId}/query
```

## Properties

| Property | Notion type | 설명 |
|---|---|---|
| `Key` | title | `rootPage`, `header`, `footer`, `head` 중 하나 |
| `Kind` | select | `page`, `blocks`, `head` 중 하나 |
| `Enabled` | checkbox | `true`인 첫 번째 동일 key row만 적용 |
| `Page` | rich text | 공식 Notion page URL 또는 32자리 page ID |
| `Data` | rich text | `head` row의 허용 필드만 가진 JSON object 문자열 |

property 이름은 대소문자를 포함해 표와 같아야 한다. `Kind`는 대소문자 차이를 허용하지만 표의 소문자 값을 권장한다. disabled row는 무시한다.

Notion URL, 하이픈 UUID, 32자리 ID는 어댑터에서 소문자 32자리 hexadecimal ID로 정규화한다. 공식 Notion 호스트가 아닌 URL, 형식이 잘못된 ID, 잘못된 property type은 설정 오류다.

## Rows

### `rootPage`

필수다.

```text
Key     = rootPage
Kind    = page
Enabled = true
Page    = 공개 범위의 루트 Notion page URL 또는 page ID
```

유효한 row가 없으면 설정 동기화는 실패하고 마지막 정상 설정과 활성 공개 범위를 유지한다. 루트 페이지 자체와 부모 정보로 확인한 모든 자식·후손 페이지가 공개 범위 후보가 된다. 본문의 링크, 멘션, 북마크, `link_to_page`는 공개 범위를 넓히지 않는다.

공개 범위 구성원은 Notion `public_url`이 있을 때만 `PUBLISHED`다. `public_url`이 없거나 `in_trash`이면 구성원은 `UNPUBLISHED`로 남고 `/posts/{postId}` 조회는 `404`를 반환한다. 미게시 부모의 구조적 후손 탐색은 계속한다.

### `header`와 `footer`

선택이다.

```text
Kind    = blocks
Enabled = true
Page    = Notion page URL 또는 page ID
```

두 참조는 별도 공개 범위를 만들지 않는다. 해당 페이지가 루트의 구조적 후손으로 활성 공개 범위에 포함되고, `PUBLISHED`이며, 정상 snapshot이 있을 때만 전역 layout fragment로 렌더링한다. 그 외에는 본문 응답을 실패시키지 않고 생략한다.

### `head`

선택이다.

```text
Kind    = head
Enabled = true
Data    = JSON object
```

허용 필드는 다음뿐이다.

| Field | 형식 | 기본값/설명 |
|---|---|---|
| `siteName` | non-blank string | `Blog` |
| `defaultDescription` | non-blank string 또는 생략 | HTML description |
| `languageTag` | BCP 47 string | `en` |
| `faviconAssetKey` | 등록된 자산 key 또는 생략 | 배포 자산 카탈로그의 명시적 current 버전 사용 |
| `presentationProfileKey` | 등록된 profile key 또는 생략 | 배포가 지정한 `notion-default` 사용 |

예시:

```json
{
  "siteName": "Notion Blog",
  "defaultDescription": "기술과 배움을 기록합니다.",
  "languageTag": "ko-KR",
  "presentationProfileKey": "notion-default"
}
```

목록 밖 필드, raw HTML, `<head>` 조각, CSS, JavaScript, 원격 자산 URL은 거부한다. Notion에서 받은 코드를 저장하거나 렌더링하지 않는다.

## Example

| Key | Kind | Enabled | Page | Data |
|---|---|---:|---|---|
| `rootPage` | `page` | true | `https://www.notion.so/Root-0123456789abcdef0123456789abcdef` |  |
| `header` | `blocks` | true | `11111111111111111111111111111111` |  |
| `footer` | `blocks` | true | `22222222222222222222222222222222` |  |
| `head` | `head` | true |  | `{"siteName":"Notion Blog","languageTag":"ko-KR"}` |

## Operational Rules

- 루트 페이지는 환경 변수가 아니라 `rootPage` row에서 선택한다.
- 설정 적용과 publication 활성화는 분리된 짧은 DB transaction으로 처리하며 Notion HTTP 호출 중 transaction을 유지하지 않는다.
- 루트 참조가 바뀌면 같은 내부 publication의 새 staging revision을 수집하고, 검증이 끝난 뒤에만 활성 revision을 교체한다.
- 설정 또는 콘텐츠 동기화 실패 시 마지막 정상 설정·게시 상태·snapshot을 유지하고 분류된 backoff를 기록한다.
- 동기화 주기와 backoff는 `blog.synchronization` 런타임 설정으로 관리한다.
- Notion API 버전은 `2026-03-11`만 지원하며 다른 값으로 시작하면 실패한다.
