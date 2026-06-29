# Notion-Blog Settings

`SETTINGS_DATABASE_ID`는 `Notion-Blog Settings` 용도의 비공개 Notion 데이터베이스를 가리킵니다. 이 데이터베이스는 일반 블로그 콘텐츠가 아니라 사이트 설정 소스이며, 현재 구현은 Notion integration으로 `client.dataSources.query({ data_source_id: SETTINGS_DATABASE_ID })`를 호출해 row를 읽습니다.

일반 콘텐츠 페이지는 Notion public share와 `public_url`을 기준으로 공개 여부가 결정되지만, settings 데이터베이스 자체는 비공개여야 합니다. 특히 `header`와 `footer`는 설정 조각으로만 읽히며 블로그 라우트로 노출되지 않습니다.

## Database Properties

MVP는 아래 5개 속성을 전제로 `SettingsRow` 형태로 파싱합니다.

| Notion property | Required | Parsed field | Type | Notes |
| --- | --- | --- | --- | --- |
| `Key` | yes | `key` | title | 고정 row key. `rootPage`, `header`, `footer`, `head` 중 하나를 사용합니다. |
| `Kind` | yes | `kind` | select | `page` \| `blocks` \| `head` |
| `Enabled` | yes | `enabled` | checkbox | `true` 인 row만 적용됩니다. |
| `Page` | yes for page rows | `page` | url or rich text | Notion page URL 또는 page ID/reference |
| `Data` | yes for head row | `data` | rich text or text | `head` row의 JSON 문자열 |

코드 기준 row shape:

```ts
interface SettingsRow {
  key: string;
  kind: "page" | "blocks" | "head";
  enabled: boolean;
  page: string;
  data: string;
}
```

## Required Rows

### `rootPage`

- 필수 row입니다.
- `Kind`는 `page`여야 합니다.
- `Enabled`는 `true`여야 합니다.
- `Page`에는 블로그의 `/` 경로로 렌더링할 Notion 페이지 URL 또는 page reference를 넣습니다.
- 파싱에 실패하거나 비어 있으면 앱은 `settings rootPage is required` 오류를 냅니다.

이 row가 가리키는 page ID가 `ParsedSettings.rootPageId`가 되고, 루트 라우트 `/`의 기준이 됩니다.

### `header`

- 선택 row입니다.
- `Kind`는 `blocks`를 사용합니다.
- `Page`에는 전역 header 블록을 읽어올 Notion 페이지 reference를 넣습니다.
- 이 페이지는 설정 조각으로만 사용되며, 일반 콘텐츠처럼 public share를 요구하지 않습니다.

### `footer`

- 선택 row입니다.
- `Kind`는 `blocks`를 사용합니다.
- `Page`에는 전역 footer 블록을 읽어올 Notion 페이지 reference를 넣습니다.
- 이 페이지도 설정 조각 전용이며 블로그 라우트로 노출되지 않습니다.

### `head`

- 선택 row입니다.
- `Kind`는 `head`를 사용합니다.
- `Data`에는 JSON 문자열을 저장합니다.
- JSON이 아니거나 허용된 shape와 다르면 `settings head has invalid JSON` 오류를 냅니다.

지원 필드는 아래와 같습니다.

```ts
interface SiteHeadSettings {
  language?: string;
  siteName?: string;
  defaultTitle?: string;
  titleTemplate?: string;
  defaultDescription?: string;
  baseUrl?: string;
  logoUrl?: string;
  faviconUrl?: string;
  ogTitle?: string;
  ogDescription?: string;
  ogImageUrl?: string;
  ogType?: string;
  twitterCard?: string;
  twitterSite?: string;
  robots?: string;
  customCss?: string;
  customHeadHtml?: string;
}
```

현재 MVP에서 실제 반영되는 값:

- `language`
- `siteName`
- `defaultTitle`
- `titleTemplate`
- `defaultDescription`
- `baseUrl`
- `faviconUrl`
- `ogTitle`
- `ogDescription`
- `ogImageUrl`
- `ogType`
- `twitterCard`
- `twitterSite`
- `robots`
- `customCss`

주의사항:

- `logoUrl`과 `customHeadHtml`은 저장은 되지만 현재 MVP 렌더러에서는 직접 사용하지 않습니다.
- 특히 `customHeadHtml`은 raw HTML을 그대로 렌더링하지 않습니다. 나중에 sanitize 또는 allowlist 정책이 생기기 전까지는 저장만 하고 출력하지 않는 값으로 봐야 합니다.

## Example Rows

아래처럼 구성하면 현재 구현과 맞습니다.

| Key | Kind | Enabled | Page | Data |
| --- | --- | --- | --- | --- |
| `rootPage` | `page` | `true` | `https://www.notion.so/Root-0123456789abcdef0123456789abcdef` | `""` |
| `header` | `blocks` | `true` | `11111111111111111111111111111111` | `""` |
| `footer` | `blocks` | `true` | `22222222222222222222222222222222` | `""` |
| `head` | `head` | `true` | `""` | `{"siteName":"Notion-Blog","defaultTitle":"Blog"}` |

## Operational Notes

- `SETTINGS_DATABASE_ID`가 유일한 settings 환경 식별자입니다.
- root page 선택은 별도 env var가 아니라 settings 데이터베이스의 `rootPage` row에서 읽습니다.
- settings sync가 끝나면 앱은 `rootPage`, `header`, `footer`가 가리키는 page ID를 refresh target으로 등록합니다.
- 일반 콘텐츠 페이지는 Notion `public_url`이 있을 때만 공개 서빙 대상이 됩니다.
