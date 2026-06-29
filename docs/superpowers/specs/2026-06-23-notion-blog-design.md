# Notion Blog 설계

## 목적

`Notion-Blog`는 Notion 페이지를 공개 블로그로 변환하는 셀프 호스팅 블로그 애플리케이션이다. 이 제품은 Notion을 글 작성 도구로 사용하면서, 별도 앱이 완성도 있는 웹사이트를 제공한다는 Oopy의 핵심 가치를 참고한다. 첫 버전은 유료 블로그 도구 없이 사용자의 기존 k3s 서버에서 실행되는 것을 목표로 한다.

이 애플리케이션은 Notion 공개 페이지를 스크래핑하지 않는다. 콘텐츠 수집, 캐싱, 라우트 생성, 렌더링은 공식 Notion API로 처리한다. 일반 콘텐츠 페이지가 블로그에 노출될 수 있는지는 Notion public share 상태를 기준으로 판단한다.

## 제품 원칙

- Notion은 콘텐츠 편집 도구이자 접근 제어 표면으로 남긴다.
- 앱은 캐시, 라우트, 리다이렉트, SEO 친화 렌더링, 헤더, 푸터, 커스텀 CSS 같은 웹 퍼블리싱 관심사를 책임진다.
- 블로그의 첫 화면은 설정 DB에 지정된 루트 Notion 페이지를 그대로 렌더링한다.
- 루트 페이지나 다른 렌더링된 페이지 안의 Notion 페이지 링크는 블로그 내부 링크로 변환하고, 사용자가 접근할 때 해당 페이지를 수집하고 캐시한다.
- 설정 데이터베이스는 Notion에서 비공개로 두고, Notion integration을 통해서만 앱이 읽는다.
- 첫 버전은 복잡한 알고리즘보다 작고 안정적이며 확장 가능한 구조를 우선한다.

## 범위

### MVP에 포함

- Notion API 기반 페이지 및 블록 수집.
- 설정 DB에 지정된 루트 페이지 렌더링.
- 렌더링 중 발견한 Notion 페이지 링크의 내부 링크 변환 및 lazy 수집.
- 각 페이지의 Notion `public_url`을 기준으로 하는 public share 기반 노출 제어.
- 전역 사이트 설정을 위한 비공개 설정 데이터베이스.
- PostgreSQL 기반 페이지 캐시, 라우트 상태, 스냅샷, slug 별칭, 갱신 상태 저장.
- 제목 기반 대표(canonical) slug 생성.
- 과거 slug 별칭을 현재 대표 slug로 직접 리다이렉트.
- 주요 블록 타입에 대한 Notion 유사 렌더링.
- 웹 프로세스와 동기화 worker 프로세스 분리.
- Docker image 및 k3s 배포용 Helm chart.

### MVP에서 제외

- Notion 공개 URL 스크래핑.
- OAuth 또는 다중 사용자 계정 관리.
- 관리자 UI.
- 다중 사이트 또는 멀티테넌트 지원.
- 페이지별 설정 override.
- 고급 갱신 스케줄링 알고리즘.
- 모든 Notion UI 세부 요소의 100% 재현.
- 페이지별 커스텀 slug override.

## 아키텍처

시스템은 세 가지 런타임 역할로 구성된다.

1. 웹 앱
   - Next.js로 실행된다.
   - 공개 블로그 라우트를 제공한다.
   - 캐시된 페이지 렌더링 중에는 PostgreSQL의 스냅샷과 라우트 테이블을 우선 읽는다.
   - 아직 캐시되지 않은 내부 Notion 페이지 링크로 접근한 경우에는 같은 동기화 파이프라인을 통해 제한된 on-demand 수집을 시도할 수 있다.

2. 동기화 worker
   - 같은 코드베이스에서 별도 프로세스 또는 k3s Deployment로 실행된다.
   - PostgreSQL에서 갱신 시점이 된 갱신 대상(refresh target)을 읽는다.
   - 요청 제한(rate limit)을 지키며 Notion API를 호출한다.
   - 설정, 페이지 메타데이터, 블록 스냅샷, 라우트 상태, slug 별칭, 갱신 상태를 업데이트한다.
   - on-demand 수집과 주기 갱신이 같은 저장 모델과 rate limit 규칙을 사용하도록 한다.

3. 마이그레이션 job
   - 배포 전 또는 배포 중 Prisma migration을 실행한다.
   - 웹과 worker가 사용하는 동일한 데이터베이스 스키마를 대상으로 한다.

PostgreSQL은 단순 임시 캐시가 아니라 퍼블리싱 상태 저장소다. 앱이 각 페이지에 대해 알고 있는 정보, 페이지가 어떤 라우트에 매핑되는지, 어떤 과거 slug가 리다이렉트되어야 하는지, 각 문서를 언제 다시 확인해야 하는지를 기록한다.

## 기술 스택

- Next.js: SSR, 라우팅, 메타데이터, 리다이렉트, sitemap 생성, React 렌더링.
- TypeScript: 웹, 렌더러, 동기화 로직, Notion 어댑터 간 공유 타입.
- Prisma: PostgreSQL 스키마 및 migration 관리.
- PostgreSQL: 영속 캐시, 라우트 상태, slug 별칭, 스냅샷, 동기화 메타데이터 저장.
- Docker image: 애플리케이션 실행 단위.
- Helm chart: 사용자의 개인 k3s 클러스터 배포 단위.

## 저장소 구조

앱 소스코드와 배포 리소스는 명확히 분리한다.

- 애플리케이션 소스, 렌더러, worker, Prisma schema는 일반 앱 소스 경로에 둔다.
- k3s 배포를 위한 Helm chart는 `deploy/helm/notion-blog`에 둔다.
- Helm chart 경로 안에는 앱 런타임 소스코드를 두지 않는다.
- 앱 코드에서는 Helm chart 내부 파일을 import하지 않는다.
- 배포 환경별 값은 chart template이 아니라 values 파일 또는 Kubernetes Secret/외부 secret 관리로 주입한다.

## Notion 모델

### 콘텐츠 루트

설정 데이터베이스의 `rootPage` 항목은 블로그의 첫 진입 페이지를 가리킨다. 이 페이지는 블로그의 `/` 경로로 렌더링한다.

앱은 루트 페이지에서 별도 그래프 탐색을 선행하지 않는다. 대신 렌더러가 Notion 페이지 링크를 만났을 때 그 링크를 블로그 내부 링크로 변환한다. 방문자가 해당 내부 링크에 접근하면 앱이 대상 페이지의 공개 상태를 확인하고, 필요하면 수집하고 캐시한다.

내부 링크 변환은 명시적인 Notion 페이지 참조만 대상으로 한다. 외부 URL은 원래 링크로 유지한다.

### 설정 데이터베이스

`SETTINGS_DATABASE_ID`는 전역 사이트 설정에 사용하는 비공개 Notion 데이터베이스를 가리킨다. 이 설정 소스는 일반 블로그 콘텐츠로 렌더링하지 않으며, 절대 블로그 라우트가 되지 않는다.

설정 데이터베이스 이름은 `Notion-Blog Settings`를 권장한다. MVP에서는 고정 key를 가진 row 기반 schema를 사용한다.

필수 속성:

- `Key`: title. 설정 key이며 `rootPage`, `header`, `footer`, `head` 값을 사용한다.
- `Kind`: select. 값의 종류이며 `page`, `blocks`, `head` 중 하나를 사용한다.
- `Enabled`: checkbox. 해당 설정 사용 여부.
- `Page`: url 또는 rich text. `rootPage`가 가리키는 Notion 페이지 URL 또는 page ID.
- `Data`: rich text. JSON이 필요한 설정을 저장한다.
- `Notes`: rich text. 사람이 읽는 설명. 앱 동작에는 사용하지 않는다.

고정 row:

- `rootPage`: `Kind = page`. `Page`에 블로그 첫 진입 Notion 페이지 URL 또는 page ID를 저장한다.
- `header`: `Kind = blocks`. 해당 row page의 본문 블록을 전역 header로 렌더링한다.
- `footer`: `Kind = blocks`. 해당 row page의 본문 블록을 전역 footer로 렌더링한다.
- `head`: `Kind = head`. `Data`에 `<head>` 및 OG/Twitter/meta 설정 JSON을 저장한다.

`header`와 `footer` row page의 본문 블록은 설정 조각으로만 읽는다. 일반 콘텐츠 페이지가 아니므로 Notion public share를 요구하지 않고, 블로그 라우트로도 노출하지 않는다.

`head` row의 `Data` JSON은 다음 필드를 지원한다.

- `language`
- `siteName`
- `defaultTitle`
- `titleTemplate`
- `defaultDescription`
- `baseUrl`
- `logoUrl`
- `faviconUrl`
- `ogTitle`
- `ogDescription`
- `ogImageUrl`
- `ogType`
- `twitterCard`
- `twitterSite`
- `robots`
- `customCss`
- `customHeadHtml`

설정 파서는 나중에 페이지별 override 섹션을 추가할 수 있는 구조화된 표현을 받아야 한다. 단, MVP에서는 페이지별 override를 구현하지 않는다.

### 공개 노출

일반 블로그 페이지는 Notion public share가 켜져 있어야 한다. 앱은 여전히 Notion API로 콘텐츠를 가져오지만, 공개 노출 여부의 최종 판단 기준은 Notion public share로 둔다.

규칙:

- 루트 페이지이거나 내부 Notion 페이지 링크로 접근한 페이지에 `public_url`이 있으면 블로그에 노출할 수 있다.
- `public_url`이 없거나 null로 바뀌면 해당 페이지는 공개 콘텐츠로 서빙하면 안 된다.
- 설정 데이터베이스는 비공개로 유지하고 Notion integration에만 공유한다.
- worker는 오래된 공개 상태 가정을 업데이트하거나 서빙하기 전에 public 상태를 갱신해야 한다.

## 갱신 모델

이 앱은 실시간 프록시가 아니라 캐시된 퍼블리셔다. Notion 변경 사항은 제한된 지연 후 블로그에 반영된다. 추적 대상마다 별도의 갱신 상태를 가진다.

핵심 필드:

- `last_synced_at`
- `next_refresh_at`
- `failure_count`
- `last_error`
- `last_edited_time`

초기 갱신 정책:

- 설정: 짧은 주기, 약 1분.
- 일반 페이지: 중간 주기, 약 10-15분.
- 실패한 대상: 단순 재시도 지연(backoff)으로 재시도.

첫 구현은 단순한 상수 기반 정책을 사용한다. 다만 나중에 last edited time, 트래픽, 우선순위 같은 휴리스틱을 worker 전체 재작성 없이 넣을 수 있도록 `RefreshPolicy` 경계를 유지한다.

권장 인터페이스 형태:

```ts
type RefreshTargetKind = "settings" | "page";

interface RefreshPolicy {
  nextRefreshAt(target: RefreshTarget, now: Date): Date;
}
```

## 동기화 흐름

1. worker가 비공개 설정 소스에서 전역 설정을 읽는다.
2. `rootPage` 설정이 가리키는 페이지를 루트 페이지 refresh target으로 등록한다.
3. 갱신 시점이 된 각 페이지에 대해 worker가 Notion page retrieval을 호출해 제목, `public_url`, `last_edited_time`을 확인한다.
4. `public_url`이 없으면 앱은 해당 페이지를 비공개로 표시하고 서빙을 중단한다.
5. 페이지가 공개 상태이고 `last_edited_time`이 바뀌었으면 worker가 block children을 재귀적으로 가져와 새 스냅샷을 저장한다.
6. 스냅샷 안의 Notion 페이지 링크는 렌더링 시 블로그 내부 링크로 변환한다.
7. 방문자가 내부 링크에 접근했는데 대상 페이지가 아직 캐시되지 않았으면, 앱은 해당 페이지를 refresh target으로 등록하고 사용 가능한 경우 즉시 수집을 시도한다.
8. worker가 제목 기반 canonical slug를 계산한다. 루트 페이지는 예외적으로 `/` 경로를 대표 route로 사용한다.
9. canonical slug가 바뀌었으면 이전 slug를 해당 페이지의 alias로 추가한다.
10. 해당 페이지의 모든 과거 slug는 현재 canonical slug로 직접 리다이렉트된다.
11. worker가 refresh policy를 통해 `next_refresh_at`을 업데이트한다.

## 라우팅과 slug

페이지의 대표(canonical) route는 Notion 페이지 제목에서 생성한다.

규칙:

- slug는 기본적으로 제목 기반이다.
- 루트 페이지는 설정 DB의 `rootPage`가 가리키는 페이지이며, 대표 route로 `/`를 사용한다.
- 한국어와 비라틴 문자 제목은 transliteration하지 않고 읽을 수 있는 형태로 slug에 남길 수 있다.
- 현재 slug가 중복되면 짧고 안정적인 page ID suffix로 구분한다.
- 제목 변경으로 slug가 바뀌면 이전 대표 slug를 별칭(alias)으로 저장한다.
- 페이지 제목이 여러 번 바뀌어도 모든 과거 slug는 최종 현재 대표 slug로 직접 리다이렉트된다.
- 별칭 리다이렉트는 301 상태를 사용한다.
- 충돌 해결에서는 대표 slug가 별칭보다 우선한다.
- 별칭 충돌은 다른 페이지의 라우트를 조용히 빼앗지 않고 비활성(inactive) 또는 충돌(conflicted) 상태로 기록한다.

예시:

```text
/hello         -> /hello-world
/hello-worlds -> /hello-world
/hello-world  -> current page
```

## 렌더링

렌더러는 실용적으로 가능한 범위에서 Notion 페이지를 최대한 가깝게 재현한다. 이 앱은 Notion 문서를 과하게 재디자인한 markdown 블로그가 아니라, 완성도 있는 블로그로 제공되는 Notion 문서처럼 느껴져야 한다.

우선 지원할 블록:

- 문단(paragraph).
- 제목(heading).
- 불릿 목록과 번호 목록.
- 할 일 블록(to-do block).
- 토글 블록(toggle block).
- 인용 블록(quote block).
- 콜아웃(callout).
- 구분선(divider).
- 코드 블록(code block).
- 이미지(image).
- 비디오 임베드(video embed).
- 파일(file).
- 북마크(bookmark).
- 표(table).
- 컬럼(column).
- 하위 페이지 링크(child page link).

렌더링 요구사항:

- 중첩 블록 구조를 보존한다.
- API가 제공하는 rich text annotation을 보존한다.
- Notion에 가까운 spacing, typography, callout color, code block, content width를 유지한다.
- 지원하지 않는 블록은 조용히 누락하지 않고 block type을 보여주는 fallback block으로 렌더링한다.
- 개별 block renderer를 독립적으로 테스트할 수 있도록 renderer 모듈을 작게 유지한다.

## 데이터 모델

초기 테이블:

### `site_settings`

최근 파싱된 전역 설정 스냅샷을 저장한다.

중요 필드:

- `id`
- `settings_json`
- `source_id`
- `last_synced_at`
- `updated_at`

### `notion_pages`

발견된 각 페이지의 현재 Notion 메타데이터를 저장한다.

중요 필드:

- `page_id`
- `title`
- `notion_url`
- `public_url`
- `is_public`
- `last_edited_time`
- `last_synced_at`
- `created_at`
- `updated_at`

### `page_routes`

현재 라우트 상태를 저장한다.

중요 필드:

- `page_id`
- `canonical_slug`
- `is_active`
- `created_at`
- `updated_at`

제약:

- 활성 라우트 사이에서 `canonical_slug`는 유일해야 한다.

### `slug_aliases`

현재 canonical route로 직접 리다이렉트할 과거 slug를 저장한다.

중요 필드:

- `id`
- `page_id`
- `slug`
- `status`
- `created_at`
- `updated_at`

제약:

- 활성 alias slug는 유일해야 한다.
- alias 해석은 redirect chain을 저장하지 않고 대상 페이지의 현재 canonical slug를 확인해야 한다.

### `page_snapshots`

렌더링 가능한 Notion block tree를 저장한다.

중요 필드:

- `page_id`
- `snapshot_json`
- `notion_last_edited_time`
- `captured_at`

### `refresh_targets`

동기화 스케줄링 상태를 저장한다.

중요 필드:

- `target_kind`
- `target_id`
- `next_refresh_at`
- `last_synced_at`
- `failure_count`
- `last_error`
- `locked_at`
- `locked_by`

worker는 여러 worker replica가 같은 대상을 동시에 처리하지 않도록 데이터베이스 수준 잠금(locking)으로 row를 점유해야 한다.

### `sync_runs`

디버깅을 위한 worker 실행 이력을 저장한다.

중요 필드:

- `id`
- `target_kind`
- `target_id`
- `status`
- `started_at`
- `finished_at`
- `error`

## 오류 처리

- 방문자 요청에서는 refresh 실패 시에도 최신 유효 스냅샷을 우선 서빙한다.
- 페이지가 비공개로 바뀌면 오래된 스냅샷이 있더라도 서빙을 중단해야 한다.
- 스냅샷이 없으면 라우트 상태에 따라 404 또는 제어된 unavailable 페이지를 반환한다.
- 지원하지 않는 Notion block은 block type을 표시하는 fallback block으로 렌더링한다.
- worker 실패는 `sync_runs`와 `refresh_targets.last_error`에 기록한다.
- 반복되는 worker 실패는 API quota를 계속 소모하지 않도록 재시도 지연(backoff)을 적용한다.

## 배포

k3s 배포는 `deploy/helm/notion-blog` Helm chart로 관리한다. chart는 애플리케이션 소스코드와 분리된 배포 리소스이며, 첫 버전 범위에 포함된다.

Helm chart는 다음 리소스를 템플릿으로 제공한다.

- Next.js server를 실행하는 web Deployment 1개.
- sync worker를 실행하는 worker Deployment 1개.
- Prisma migration을 위한 migration Job 1개.
- 웹 앱을 위한 Service와 Ingress.
- `env.existingSecret`로 참조되는 Kubernetes Secret. 이 Secret은 다음 환경 변수를 제공한다.
  - `DATABASE_URL`
  - `NOTION_TOKEN`
  - `SETTINGS_DATABASE_ID`
- 웹 앱 readiness probe와 liveness probe.
- 진행 중인 sync job을 마치거나 lock을 해제할 수 있는 worker graceful shutdown.

초기 values는 다음 설정을 노출한다.

- `image.repository`
- `image.tag`
- `image.pullPolicy`
- `web.replicas`
- `worker.replicas`
- `ingress.enabled`
- `ingress.className`
- `ingress.hosts`
- `resources`
- `env.existingSecret`

기존 PostgreSQL은 chart가 직접 생성하지 않는다. 데이터베이스 접속 정보는 `DATABASE_URL`을 가진 Kubernetes Secret 또는 외부 secret 관리 도구를 통해 주입한다.

## 테스트 전략

단위 테스트:

- slug 생성.
- slug 충돌 처리.
- alias 해석.
- refresh policy.
- 설정 파싱.
- 개별 block renderer.

통합 테스트:

- Prisma schema 제약.
- route lookup 및 redirect 동작.
- mock Notion API 응답을 사용한 page metadata 및 snapshot worker 처리.
- public/private 전환 동작.
- Helm chart template 렌더링.

엔드투엔드 확인:

- 공개 Notion page snapshot이 웹 앱을 통해 렌더링된다.
- slug 별칭이 현재 대표 route로 직접 리다이렉트된다.
- `public_url`이 없는 페이지는 서빙되지 않는다.
- custom CSS와 전역 설정이 적용된다.
- Helm chart로 web, worker, migration Job, Service, Ingress가 생성된다.

## 열린 확장 지점

아래 항목은 의도적으로 열어둔 확장 지점이며 MVP 작업이 아니다.

- 페이지별 설정 override.
- 고급 refresh policy.
- 관리자 UI.
- 수동 refresh endpoint.
- 다중 사이트 지원.
- 더 넓은 Notion 링크 해석.
- 더 완전한 Notion block coverage.
- 공개 Notion URL import.

## 승인된 결정

이 설계는 다음 승인된 선택을 반영한다.

- 공개 페이지 스크래핑이 아니라 공식 Notion API를 사용한다.
- 일반 페이지의 접근 제어는 Notion public share를 기준으로 한다.
- 설정은 private으로 유지하고 integration으로만 접근한다.
- 프로젝트 이름은 `Notion-Blog`다.
- 설정 DB의 `rootPage`가 가리키는 페이지를 `/`에 렌더링한다.
- 별도 그래프 탐색을 선행하지 않고, 렌더링된 Notion 페이지 링크를 내부 링크로 변환해 lazy 수집한다.
- 확장 가능한 policy 경계를 유지하되, 초기에는 단순한 target별 refresh interval을 사용한다.
- 페이지 제목에서 route를 생성한다.
- 모든 과거 slug는 최신 대표 slug로 직접 리다이렉트한다.
- 설정 데이터베이스 MVP 범위는 `rootPage`, `header`, `footer`, `head`로 제한한다.
- 페이지는 실용적으로 가능한 범위에서 Notion에 가깝게 렌더링한다.
- 사용자의 k3s 클러스터에 배포한다.
- 첫 버전에 k3s 배포용 Helm chart를 포함한다.
- Helm chart는 앱 소스와 분리된 `deploy/helm/notion-blog` 경로에 둔다.
- PostgreSQL, Next.js, TypeScript, Prisma, 별도 sync worker를 사용한다.
