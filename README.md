# Notion Blog

Notion을 편집 도구로 사용하는 셀프 호스팅 블로그입니다. 게시글은 다양한 블록의 트리로 정규화되어 PostgreSQL에 스냅샷으로 저장되고, 웹 요청은 저장된 스냅샷을 서버에서 렌더링합니다. 따라서 Notion이 일시적으로 응답하지 않아도 마지막으로 확인한 공개 콘텐츠를 계속 제공할 수 있습니다.

## Stack

- JDK 25
- Kotlin 2.3
- Spring Boot 4.1
- Spring MVC + Thymeleaf
- JetBrains Exposed JDBC DSL
- PostgreSQL + Flyway
- Gradle Kotlin DSL
- Docker

웹 요청과 동기화 스케줄러는 하나의 Spring Boot 애플리케이션에서 실행됩니다. 별도 worker나 migration 프로세스는 없습니다.

전체 설계와 결정은 [Kotlin/Spring 아키텍처](docs/kotlin-spring-architecture.md), Notion 설정 데이터 소스의 행 계약은 [Notion 설정 스키마](docs/notion-settings-schema.md), 에이전트 작업 규칙은 [AGENTS.md](AGENTS.md)를 참고합니다.

## Requirements

- JDK 25
- PostgreSQL 16 이상 권장
- Docker: Testcontainers PostgreSQL 통합 테스트에 필요

시스템 Gradle 설치는 필요하지 않습니다. 저장소의 Gradle Wrapper를 사용합니다.

## Configuration

환경변수 예시는 [.env.example](.env.example)에 있습니다.

필수 환경변수는 다음 다섯 개입니다.

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
NOTION_TOKEN
NOTION_SETTINGS_DATA_SOURCE_ID
```

그 외 환경변수는 `application.yml`에 기본값이 있습니다. 주요 선택 설정은 다음과 같습니다.

```text
SERVER_PORT
NOTION_SOURCE_ID
NOTION_API_VERSION       # 2026-03-11만 지원
NOTION_BASE_URL
NOTION_REQUEST_TIMEOUT
NOTION_COLLECTION_TIMEOUT
NOTION_MAX_BLOCK_DEPTH
NOTION_MAX_BLOCK_COUNT
BLOG_SYNCHRONIZATION_ENABLED
BLOG_SYNCHRONIZATION_INTERVAL_MS
BLOG_SYNCHRONIZATION_DUE_BATCH_SIZE
BLOG_SYNCHRONIZATION_SUCCESS_INTERVAL
BLOG_SYNCHRONIZATION_INITIAL_FAILURE_DELAY
BLOG_SYNCHRONIZATION_MAXIMUM_FAILURE_DELAY
```

`NOTION_TOKEN`과 데이터베이스 자격 증명은 저장소나 container image에 넣지 말고 실행 환경에서 주입합니다. `NOTION_API_VERSION`은 현재 `2026-03-11`로 고정되어 다른 값으로 시작할 수 없습니다. 사이트 루트와 선택적인 header/footer/head 설정은 환경변수가 아니라 [Notion 설정 스키마](docs/notion-settings-schema.md)의 설정 data source에서 가져옵니다.

## Local Development

환경변수를 준비한 뒤 애플리케이션을 실행합니다.

```bash
cp .env.example local.env
set -a
source local.env
set +a
./gradlew bootRun
```

기본 포트는 `8080`입니다. 시작 시 Flyway가 스키마를 적용합니다. 기본 표현 프로필과 최초 설정 동기화 대상은 Flyway의 seed `sync_state`에서 등록되므로, scheduler가 활성화된 빈 DB에서도 설정 data source pull을 시작할 수 있습니다. 아직 활성 공개 범위나 루트 스냅샷이 없으면 `/`와 게시글 경로는 초기화 중이라는 의미로 `503`을 반환합니다.

## Runtime Behavior

웹 공개 경로는 두 개뿐입니다.

- `GET /`: 활성 공개 범위의 루트 게시글
- `GET /posts/{postId}`: 내부 `PostId`로 조회하는 게시글

공개 범위는 Notion 설정의 루트 페이지와 구조적으로 확인된 자식·후손 페이지로 구성됩니다. 본문 링크, 멘션, 북마크, `link_to_page`는 공개 범위를 넓히지 않습니다.

공개 범위 구성원이라도 Notion에서 `public_url`이 없거나 휴지통 상태면 `UNPUBLISHED`로 기록되고 해당 내부 경로는 `404`를 반환합니다. 미게시 부모의 구조적 후손 탐색은 계속됩니다. 외부 장애나 매핑 실패는 미게시로 바꾸지 않고 마지막 게시 상태와 스냅샷을 보존합니다.

동기화는 `BLOG_SYNCHRONIZATION_*` 설정에 따라 due `sync_state` 대상을 처리합니다. 성공 시 다음 성공 주기를 예약하고, 실패 시 분류된 오류와 지수 백오프를 기록합니다. 외부 HTTP 호출과 PostgreSQL transaction은 분리되어 있으며, Notion 장애는 readiness 실패로 연결하지 않습니다.

## Verification

```bash
./gradlew ktlintCheck
./gradlew test
./gradlew build
```

테스트는 다음 경계를 검증합니다.

- domain: 게시글·블록 트리 불변식, 공개 범위와 게시 상태, 링크와 동기화 상태
- Notion adapter: `2026-03-11` 블록 응답 36개 대안, 페이지네이션, 재귀 자식 수집, ID 정규화, 오류 분류와 제한
- application: 공개 범위 staging/activation, 미게시 취소, 링크 해석, 외부 호출과 transaction 분리, 설정 적용
- persistence: Flyway, PostgreSQL 제약, Exposed 매핑, JSONB 스냅샷 왕복과 rollback
- web/rendering: `/` 및 `/posts/{postId}`의 `200`·`404`·`503`, 의미론적 HTML, escaping, CSP, 안전한 미디어와 표현 자산
- scheduling/configuration: due 대상 선택, 주입된 clock 기준 상태 전이, seed된 최초 site sync, 타입 안전한 환경 설정

PostgreSQL 경계 테스트는 H2로 대체하지 않으며 Docker가 필요합니다. Rancher Desktop에서 Testcontainers가 기본 socket을 찾지 못하는 경우에는 로컬 환경에 맞는 Docker socket을 지정한 뒤 실행합니다.

```bash
DOCKER_HOST=unix://$HOME/.rd/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
./gradlew test
```

`TESTCONTAINERS_HOST_OVERRIDE` 값은 Rancher Desktop 구성에 따라 달라질 수 있습니다.

Kotlin 포맷을 자동 수정하려면 다음 명령을 사용합니다.

```bash
./gradlew ktlintFormat
```

## Docker

```bash
docker build -t notion-blog:local .
docker run --rm --env-file local.env -p 8080:8080 notion-blog:local
```

Runtime container는 UID/GID `10001:10001`로 실행됩니다. read-only root filesystem, `/tmp` volume과 같은 실행 정책은 외부 배포 하네스에서 설정합니다.

## Deployment Boundary

이 저장소는 애플리케이션 소스, Gradle/Flyway 설정, Dockerfile과 CI workflow만 소유합니다. Helm chart, Kubernetes manifest, GitOps 설정, Secret/ConfigMap 주입은 별도 하네스 저장소에서 관리합니다.

애플리케이션은 `8080` 포트와 `/actuator/health/liveness`, `/actuator/health/readiness` endpoint를 제공합니다. 현재 scheduler는 다중 인스턴스 조정을 하지 않으므로 scheduler를 활성화한 인스턴스는 하나여야 합니다.
