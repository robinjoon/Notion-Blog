# Notion Blog Settings Data Source

`NOTION_SETTINGS_DATA_SOURCE_ID`는 전역 블로그 설정을 저장하는 비공개 Notion data source ID입니다. 설정 source 자체는 공개하지 않고 Notion integration에만 공유합니다.

애플리케이션은 다음 endpoint를 cursor 끝까지 조회합니다.

```text
POST /v1/data_sources/{dataSourceId}/query
```

## Properties

아래 property 이름을 사용합니다.

| Property | Notion type | 설명 |
|---|---|---|
| `Key` | title | `rootPage`, `header`, `footer`, `head` 중 하나 |
| `Kind` | select | `page`, `blocks`, `head` 중 하나 |
| `Enabled` | checkbox | `true`인 첫 번째 동일 key row만 적용 |
| `Page` | URL 또는 rich text | Notion page URL 또는 32자리 page ID |
| `Data` | rich text | `head` row의 JSON object 문자열 |

property 이름은 대소문자를 포함해 표와 같아야 합니다. `Kind` 값은 대소문자 차이를 허용하지만 표의 소문자 값을 권장합니다.

## Rows

### `rootPage`

필수입니다.

```text
Key     = rootPage
Kind    = page
Enabled = true
Page    = 공개할 루트 Notion page URL 또는 page ID
```

유효한 `rootPage`가 없으면 설정 갱신은 실패합니다. 성공하면 이 page가 `/` route의 소유자가 되며, 설정 저장·page 발견·root route 교체는 같은 DB transaction에서 수행됩니다.

일반 콘텐츠와 마찬가지로 실제 HTML을 제공하려면 해당 Notion page에 `public_url`이 있어야 합니다.

### `header`와 `footer`

선택입니다.

```text
Kind    = blocks
Enabled = true
Page    = Notion page URL 또는 page ID
```

현재 구현은 ID를 저장하고 갱신 대상으로 발견하지만 전역 layout fragment로 렌더링하지 않습니다. 향후 기능을 위한 예약 설정입니다.

### `head`

선택입니다.

```text
Kind    = head
Enabled = true
Data    = JSON object
```

현재 구현은 JSON object인지 검증해 PostgreSQL JSONB에 저장합니다. raw HTML과 `customHeadHtml`은 보안상 렌더링하지 않으며, 저장된 head 설정을 페이지별 metadata에 적용하는 기능도 아직 활성화하지 않았습니다.

예시:

```json
{
  "siteName": "Notion Blog",
  "defaultDescription": "기술과 배움을 기록합니다."
}
```

## Example

| Key | Kind | Enabled | Page | Data |
|---|---|---:|---|---|
| `rootPage` | `page` | true | `https://www.notion.so/Root-0123456789abcdef0123456789abcdef` |  |
| `header` | `blocks` | true | `11111111111111111111111111111111` |  |
| `footer` | `blocks` | true | `22222222222222222222222222222222` |  |
| `head` | `head` | true |  | `{"siteName":"Notion Blog"}` |

## Operational Rules

- root page는 환경변수가 아니라 `rootPage` row에서 선택합니다.
- 명시적인 Notion page link로 발견된 ID만 lazy 수집 경로에서 허용됩니다.
- 공개 page는 Notion `public_url`이 있을 때만 route와 snapshot을 제공합니다.
- 설정 갱신 기본 주기는 1분, page 기본 주기는 15분입니다.
- 실패 추가 지연은 5분부터 지수 증가하고 60분에서 제한됩니다.
- Notion API version은 `NOTION_API_VERSION`으로 고정합니다.
