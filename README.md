# Notion Blog

Notion을 편집 도구로 사용하고, 공개 페이지를 자체 도메인의 서버 렌더링 블로그로 제공하는 셀프 호스팅 애플리케이션입니다.

Notion API에서 가져온 페이지와 블록은 PostgreSQL에 퍼블리싱 스냅샷으로 저장됩니다. 방문 요청은 저장된 스냅샷을 먼저 렌더링하므로 Notion이 일시적으로 응답하지 않아도 기존 공개 콘텐츠를 계속 제공할 수 있습니다.

## Stack

- JDK 25
- Kotlin 2.3
- Spring Boot 4.1
- Spring MVC + Thymeleaf
- JetBrains Exposed JDBC DSL
- PostgreSQL + Flyway
- Gradle Kotlin DSL
- Docker

별도 worker나 migration 프로세스는 없습니다. 하나의 Spring Boot 프로세스가 웹 요청, Flyway migration, Notion 주기 갱신을 담당합니다.

전체 설계와 결정은 [Kotlin/Spring 재구축 설계](docs/kotlin-spring-architecture.md), 에이전트 작업 규칙은 [AGENTS.md](AGENTS.md)에 있습니다.

## Requirements

- JDK 25
- PostgreSQL 16 이상 권장
- Docker: Testcontainers 통합 테스트와 이미지 build에 필요

시스템 Gradle 설치는 필요하지 않습니다. 저장소의 Gradle Wrapper를 사용합니다.

## Configuration

예시는 [.env.example](.env.example)에 있습니다.

필수 변수:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
NOTION_TOKEN
NOTION_SETTINGS_DATA_SOURCE_ID
NOTION_API_VERSION
BLOG_BASE_URL
```

Notion token은 repository나 container image에 직접 넣지 않습니다. 실행 환경에서 환경변수로 주입합니다.

Notion 설정 data source의 property와 row 구성은 [Notion 설정 스키마](docs/notion-settings-schema.md)를 참고합니다.

## Local Development

환경변수를 준비한 다음 애플리케이션을 실행합니다.

```bash
cp .env.example local.env
set -a
source local.env
set +a
./gradlew bootRun
```

기본 포트는 `8080`입니다. 시작 시 Flyway가 스키마를 적용하고, scheduler가 설정 data source를 bootstrap합니다. 아직 루트 스냅샷이 없으면 `/`는 초기화 중이라는 의미로 `503`을 반환합니다.

## Verification

```bash
./gradlew ktlintCheck
./gradlew test
./gradlew build
```

Kotlin과 Gradle Kotlin DSL의 포맷을 자동 수정하려면 다음 명령을 사용합니다.

```bash
./gradlew ktlintFormat
```

루트 [`.editorconfig`](.editorconfig)가 Kotlin 코드 스타일의 기준입니다. IntelliJ에서는 EditorConfig 지원을 켜고 `Detect and use existing file indents for editing`을 끕니다. 내장 Kotlin 포매터를 사용하되 저장 시 `Rearrange code`와 `Run code cleanup`은 함께 실행하지 않습니다. 최종 검사는 Gradle의 `ktlintCheck`가 담당합니다.

테스트 구성:

- domain: slug, Notion page ID, route/visibility, refresh backoff
- Notion boundary: pagination, recursive blocks, deadline, mapping, error classification
- persistence boundary: Flyway, JSONB, `timestamptz`, FK/unique, route ownership, rollback
- application: public/private refresh, lazy collection, stale refresh, settings bootstrap
- web/rendering: HTTP status, redirects, complete HTML, block rendering, unsafe link 차단
- repository boundary: 배포 하네스 manifest 미포함, Java 25 container 계약

Rancher Desktop에서 Testcontainers가 기본 socket과 published host를 찾지 못하면 환경에 맞는 값을 지정합니다.

```bash
DOCKER_HOST=unix://$HOME/.rd/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
./gradlew test
```

`TESTCONTAINERS_HOST_OVERRIDE`는 로컬 Rancher Desktop VM 구성에 따라 다를 수 있습니다.

## Docker

```bash
docker build -t notion-blog:local .
docker run --rm --env-file local.env -p 8080:8080 notion-blog:local
```

Runtime container는 UID/GID `10001`로 실행됩니다. read-only root filesystem과 `/tmp` 쓰기 볼륨 같은 실행 정책은 외부 하네스에서 설정합니다.

## Deployment Boundary

이 저장소는 애플리케이션 소스, Gradle/Flyway 설정, Dockerfile과 CI workflow만 소유합니다. Helm chart, Kubernetes manifest, GitOps 설정은 별도 하네스 저장소에서 관리합니다.

외부 하네스에는 생성한 image와 [Configuration](#configuration)의 환경변수를 전달하면 됩니다. 애플리케이션은 `8080` 포트와 `/actuator/health/liveness`, `/actuator/health/readiness` endpoint를 제공합니다. 현재 scheduler는 다중 인스턴스 조정을 하지 않으므로 동시에 scheduler를 활성화하는 인스턴스는 하나여야 합니다.

### Home k3s delivery

GitHub Actions는 pull request에서 Gradle build만 검증합니다. `master` push 또는 `master` ref의 수동 실행에서는 검증을 통과한 `linux/amd64` 이미지를 다음 형식의 불변 태그로 홈 Zot 레지스트리에 게시합니다. 아직 기본 브랜치에 병합되지 않은 최초 배포만 예약된 `bootstrap-homelab` tag로 시작합니다.

```text
registry.homelab.robinjoon.xyz/apps/notion-blog:sha-<full-git-sha>-run-<run-id>-<attempt>
```

재실행도 기존 tag를 덮어쓰지 않도록 Actions run ID와 attempt를 tag에 포함합니다. CI는 image 게시까지만 담당합니다. 배포 하네스 변경은 `/Users/imsubin/IdeaProjects/Simple-K3S-Herness`에서 별도 Codex 세션으로 하네스 CLI를 사용해 수행하고, Argo CD가 하네스 `main`을 동기화합니다.

필요한 GitHub Actions 설정:

```text
Variable: HOMELAB_REGISTRY_HOST
Variable: HOMELAB_REGISTRY_IMAGE
Secret:   HOMELAB_REGISTRY_USERNAME
Secret:   HOMELAB_REGISTRY_PASSWORD
```

최초 배포용 `bootstrap-homelab` tag는 저장소의 유일한 관리자가 한 번만 생성하며, 수동 게시 작업은 `master` ref에서만 허용합니다.

Notion runtime 값은 별도의 GitHub Actions Secret `NOTION_TOKEN`, `NOTION_SETTINGS_DATA_SOURCE_ID`에도 보관합니다. 이 두 값은 image build에 전달하지 않으며, k3s runtime Secret은 하네스 밖의 운영 절차로 주입합니다. DB 자격 증명은 k3s에만 유지합니다.

## Runtime Behavior

- `/`: 설정의 `rootPage`를 렌더링합니다.
- `/{slug}`: 공개 canonical page를 렌더링합니다.
- 과거 alias: 현재 canonical path로 `301` 응답합니다.
- `/notion/{pageId}`: 이미 콘텐츠에서 발견한 page만 제한된 시간 안에 수집하고 canonical path로 `303` 응답합니다.
- stale snapshot: 즉시 렌더링하고 bounded executor로 갱신을 요청합니다.
- 비공개 전환: snapshot은 보존하지만 모든 공개 route를 비활성화합니다.
- 정상 갱신: scheduler와 설정/page metadata 확인에 같은 1분 주기를 사용하고, 변경된 page만 전체 block을 다시 수집합니다.
- page 갱신 실패: 기존 snapshot을 유지하고 2분에 1~30초의 random jitter를 더한 뒤 다시 시도합니다.
- 설정 갱신 실패: 기존 snapshot을 유지하고 지수 backoff를 적용합니다.
