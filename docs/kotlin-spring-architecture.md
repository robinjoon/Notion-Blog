# Notion Blog Kotlin/Spring 재구축 설계

## 1. 문서 상태

- 상태: 구현 기준선
- 대상 브랜치: `codex/notion-blog-mvp`
- 런타임 기준: JDK 25
- 작성 목적: 기존 Next.js/TypeScript/Prisma 구현을 Kotlin/Spring Boot/Exposed 기반으로 전면 교체한다.

이 문서는 새 구현의 구조와 경계를 정의한다. 구현 중 발견된 사실 때문에 설계가 바뀌면 코드보다 이 문서를 먼저 갱신하고, 변경 이유를 결정 기록에 남긴다.

## 2. 제품 목표

Notion Blog는 Notion을 콘텐츠 편집 도구로 사용하면서, 공개 가능한 페이지를 자체 도메인의 서버 렌더링 블로그로 제공하는 셀프 호스팅 애플리케이션이다.

애플리케이션은 다음을 책임진다.

- 비공개 Notion 설정 데이터 소스에서 사이트 설정을 읽는다.
- Notion의 공개 상태를 확인한 페이지만 블로그에 노출한다.
- 페이지와 블록을 PostgreSQL에 스냅샷으로 저장한다.
- 제목 기반 canonical 경로와 과거 경로 alias를 관리한다.
- Notion 페이지 링크를 내부 lazy 수집 경로로 변환한다.
- 저장된 스냅샷을 HTML로 서버 렌더링한다.
- Notion 장애 중에도 이미 저장된 공개 콘텐츠를 제공한다.

## 3. 범위

### 3.1 구현 범위

- 루트 페이지 `/` 렌더링
- canonical 페이지 경로 렌더링
- 과거 alias의 현재 canonical 경로 `301` 리다이렉트
- `/notion/{pageId}`를 통한 알려진 Notion 페이지 lazy 수집
- Notion 페이지 공개 상태 반영
- 설정, 페이지 메타데이터 및 블록의 주기적 갱신
- 주요 Notion 블록과 rich text 렌더링
- Flyway 기반 PostgreSQL 스키마 관리
- Actuator 기반 liveness/readiness
- 단일 OCI container image

### 3.2 초기 범위에서 제외

- OAuth와 다중 사용자
- 관리자 UI
- 멀티사이트 및 멀티테넌시
- SPA 프런트엔드
- WebFlux/R2DBC
- 별도 worker Deployment
- 다중 replica에서의 scheduler leader election
- Notion 공개 페이지 HTML 스크래핑
- Helm chart와 Kubernetes/배포 하네스 manifest

## 4. 핵심 결정

### ADR-001: 단일 배포형 모놀리스

웹 요청과 정기 동기화를 하나의 Spring Boot 프로세스에서 실행한다. 두 진입점은 같은 애플리케이션 서비스를 호출한다.

이유:

- 현재 트래픽과 운영 규모에서 독립적인 worker 확장이 필요하지 않다.
- 기존 web/worker 사이의 코드와 트랜잭션 경계 중복을 제거한다.
- 별도 프로세스와 배포 artifact를 늘리지 않고 하나의 이미지로 실행할 수 있다.

제약:

- 동시에 scheduler가 활성화되는 애플리케이션 인스턴스는 하나여야 한다.
- 다중 인스턴스에서 scheduler를 활성화하기 전에 PostgreSQL advisory lock, DB lease 또는 leader election을 도입해야 한다.

### ADR-002: 동기식 요청 모델

Spring MVC, Spring `RestClient`, Exposed JDBC를 사용한다. WebFlux, R2DBC, 코루틴 기반 DB 접근을 섞지 않는다.

이유:

- Exposed JDBC 트랜잭션과 일관된 실행 모델을 유지한다.
- 개인 블로그의 처리량 요구에 충분하다.
- 하나의 동시성 모델만 사용해 장애와 테스트 복잡성을 줄인다.

### ADR-003: Exposed DSL과 Spring 트랜잭션

- Exposed DAO를 사용하지 않고 DSL만 사용한다.
- 애플리케이션 서비스 메서드에 `@Transactional`을 적용한다.
- repository는 임의로 `transaction {}`을 중첩하지 않는다.
- Exposed의 `Table`, `ResultRow`, SQL 식은 persistence adapter 밖으로 노출하지 않는다.

### ADR-004: Flyway가 스키마의 기준

- 운영환경에서 Exposed 자동 DDL 생성을 사용하지 않는다.
- 모든 스키마 변경은 순서가 있는 Flyway migration으로 수행한다.
- Exposed Table 정의는 migration 결과를 표현하며 migration을 대체하지 않는다.

### ADR-005: PostgreSQL은 퍼블리싱 상태 저장소

PostgreSQL은 폐기 가능한 HTTP 캐시가 아니다. 공개 상태, 라우트, alias, 마지막 정상 스냅샷과 갱신 상태를 보존한다.

### ADR-006: stale 콘텐츠 우선 제공

저장된 공개 스냅샷이 있으면 갱신 시점이 지났더라도 먼저 렌더링하고 갱신을 요청한다. Notion 장애는 readiness 실패 사유가 아니다. 단, 마지막 공개 상태 확인 이후 설정된 공개 상태 TTL을 넘긴 페이지의 정책은 별도 테스트로 고정한다.

## 5. 기술 기준

| 영역 | 선택 |
|---|---|
| JDK | 25 toolchain |
| 언어 | Kotlin, Spring Boot가 관리하는 호환 버전 |
| 웹 | Spring MVC, Thymeleaf |
| 외부 HTTP | Spring `RestClient` |
| DB | PostgreSQL |
| DB 접근 | JetBrains Exposed JDBC DSL |
| 스키마 | Flyway |
| 직렬화 | Jackson Kotlin, JSONB |
| 운영 | Actuator, Micrometer |
| 테스트 | JUnit 5, AssertJ, MockK, Testcontainers, MockWebServer |
| 빌드 | Gradle Kotlin DSL, Gradle Wrapper |
| 배포 artifact | 단일 OCI image; orchestration manifest는 외부 하네스 저장소 소유 |

버전은 동적 범위로 선언하지 않는다. 최소 호환성 테스트가 통과한 정확한 버전을 Gradle 파일과 wrapper에 고정한다.

## 6. 런타임 구조

```text
                         +----------------+
                         |   Notion API   |
                         +--------^-------+
                                  | RestClient
                                  |
HTTP -> Spring Boot Application -> PostgreSQL
                           |               |
                           |               +-- Exposed JDBC DSL
                           |               +-- Flyway migrations
                           |
                           +-- Spring MVC controllers
                           +-- Thymeleaf renderer
                           +-- application services
                           +-- refresh coordinator
                           +-- @Scheduled synchronizer
                           +-- Actuator endpoints
```

## 7. 소스 구조와 의존 방향

단일 Gradle 모듈로 시작한다.

```text
src/main/kotlin/<base-package>/
├── BlogApplication.kt
├── config/
├── domain/
│   ├── model/
│   └── policy/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
├── adapter/
│   ├── in/web/
│   └── out/
│       ├── notion/
│       ├── persistence/
│       └── rendering/
└── scheduling/
```

허용되는 의존 방향:

```text
adapter -> application -> domain
config  -> adapter/application
domain  -> Kotlin/JDK only
```

핵심 도메인에 프레임워크 annotation을 넣지 않는다. 단순한 유스케이스 하나를 위해 인터페이스를 만들지는 않는다. 외부 시스템 또는 영속성처럼 실제 교체·테스트 경계가 있는 곳에만 port를 둔다.

## 8. 데이터 모델

### 8.1 `site_settings`

| 열 | 설명 |
|---|---|
| `id` | 단일 사이트 식별자 |
| `settings_data_source_id` | Notion 설정 data source ID, unique |
| `root_page_id` | `/`에 연결되는 Notion page ID |
| `header_page_id` | 선택적 header 블록 원본 |
| `footer_page_id` | 선택적 footer 블록 원본 |
| `head_json` | 허용된 사이트 메타데이터 JSONB |
| `last_synced_at` | 마지막 정상 동기화 시각 |
| `refresh_after` | 다음 갱신 가능 시각 |
| `failure_count` | 연속 실패 횟수 |
| `last_error` | 마지막 실패 요약 |

### 8.2 `notion_page`

| 열 | 설명 |
|---|---|
| `page_id` | Notion page ID, PK |
| `title` | 마지막으로 확인한 제목 |
| `notion_url` | 원본 URL |
| `public_url` | 공개 URL, 비공개면 null |
| `visibility` | `DISCOVERED`, `PUBLIC`, `PRIVATE` |
| `notion_last_edited_at` | Notion 수정 시각 |
| `last_synced_at` | 마지막 정상 동기화 시각 |
| `refresh_after` | 다음 갱신 가능 시각 |
| `failure_count` | 연속 실패 횟수 |
| `last_error` | 마지막 실패 요약 |

### 8.3 `page_snapshot`

| 열 | 설명 |
|---|---|
| `page_id` | `notion_page` FK, PK |
| `snapshot_json` | 정규화된 페이지 블록 JSONB |
| `notion_last_edited_at` | 스냅샷 기준 수정 시각 |
| `captured_at` | 저장 시각 |

### 8.4 `page_route`

| 열 | 설명 |
|---|---|
| `path` | `/`로 시작하는 전역 유일 경로, PK |
| `page_id` | `notion_page` FK |
| `kind` | `ROOT`, `CANONICAL`, `ALIAS` |
| `active` | 현재 라우팅에 사용 가능한지 여부 |
| `created_at` | 생성 시각 |

제약:

- 하나의 page에는 활성 canonical 경로가 최대 하나다.
- 활성 root 경로는 전체에서 하나다.
- canonical 경로가 alias보다 우선한다.
- 제목 변경 시 이전 canonical 경로는 같은 page의 alias가 된다.

별도 `refresh_target` 테이블은 초기 구현에서 만들지 않는다. 설정과 페이지 레코드의 `refresh_after`로 충분하다. 별도 `sync_run` 테이블도 운영 필요가 확인되기 전까지 로그와 metric으로 대체한다.

## 9. 주요 흐름

### 9.1 애플리케이션 시작

1. Flyway가 migration을 적용한다.
2. Spring 컨텍스트와 Exposed 연결을 초기화한다.
3. 저장된 루트 스냅샷이 없으면 초기화 상태를 반환하고 bootstrap 동기화를 요청한다.
4. 저장된 스냅샷이 있으면 Notion 상태와 무관하게 읽기 경로를 준비한다.

### 9.2 공개 페이지 요청

1. `page_route.path`로 경로를 찾는다.
2. alias이면 현재 canonical 경로로 `301` 응답한다.
3. root/canonical이면 공개 page와 snapshot을 읽는다.
4. snapshot을 Thymeleaf view model로 바꿔 렌더링한다.
5. `refresh_after`가 지났으면 응답을 막지 않고 중복 제거된 갱신을 요청한다.

### 9.3 lazy 수집

1. 렌더링 과정에서 발견한 명시적인 Notion page ID를 `DISCOVERED` 상태로 기록한다.
2. `/notion/{pageId}`는 이미 발견된 ID만 허용한다. 임의의 Notion page proxy로 사용하지 않는다.
3. 캐시가 있으면 canonical 경로로 이동한다.
4. 캐시가 없으면 제한된 시간 안에 공개 상태와 콘텐츠를 확인한다.
5. 공개 페이지면 저장 후 canonical 경로로 redirect하고, 아니면 `404`를 반환한다.

### 9.4 갱신

1. 설정 또는 page의 메타데이터를 가져온다.
2. page의 `public_url`이 null이면 page를 `PRIVATE`으로 전환하고 활성 경로를 한 트랜잭션에서 비활성화한다.
3. 수정 시각이 같으면 공개 상태와 갱신 시각만 저장한다.
4. 수정된 공개 page이면 모든 block children 페이지를 재귀적으로 수집한다.
5. Notion DTO를 정규화된 domain snapshot으로 변환한다.
6. page, snapshot, route, 발견된 링크를 한 트랜잭션에서 반영한다.
7. 실패하면 기존 snapshot을 보존하고 지수 backoff를 적용한다. 성공 시 기본 주기는 설정 1분, page 15분이다. 실패 추가 지연은 `5분 * 2^(failureCount-1)`이며 60분을 상한으로 한다.

### 9.5 스케줄링

- `@Scheduled` 진입점은 due 대상 ID만 배치로 조회한다.
- 실제 갱신은 HTTP lazy 수집과 같은 application service가 담당한다.
- 같은 프로세스에서 동일 ID의 동시 갱신을 합친다.
- Notion 호출 동시성은 작은 고정 값으로 제한한다.

## 10. Notion 경계

- API version을 `NOTION_API_VERSION`으로 명시하고 배포 설정에서 고정한다.
- 모든 cursor 페이지를 수집한다.
- block child 구조를 보존한다.
- Notion API DTO는 adapter 밖으로 노출하지 않는다.
- `429`, timeout, `5xx`는 재시도 가능한 실패로 분류한다.
- 인증/권한 오류와 잘못된 설정은 운영자가 조치할 수 있는 오류로 구분한다.
- 원격 응답 전체나 token을 로그에 남기지 않는다.

## 11. 렌더링 경계

```text
Notion API DTO -> NotionBlock -> BlockView -> Thymeleaf fragment
```

우선 지원 블록:

- paragraph, heading 1-3
- bulleted/numbered list item
- to-do, toggle, quote, callout, divider
- code, image, video, file, bookmark
- table, column, child page

지원하지 않는 블록은 조용히 버리지 않고 타입을 나타내는 안전한 fallback을 렌더링한다. 외부 URL은 허용된 scheme만 링크로 만들며, 커스텀 head HTML은 그대로 삽입하지 않는다.

## 12. 트랜잭션 경계

다음 변경은 반드시 하나의 application service 트랜잭션에서 수행한다.

- 공개 page와 snapshot 저장
- canonical 변경과 이전 alias 생성
- 비공개 전환과 route 비활성화
- 설정 root 변경과 root route 교체

Notion HTTP 호출 중에는 DB 트랜잭션을 열어 두지 않는다. 먼저 외부 데이터를 수집·검증하고, 짧은 저장 트랜잭션에서 상태를 반영한다.

## 13. 테스트 전략

모든 기능은 실패하는 테스트를 먼저 작성하고 최소 구현으로 통과시킨다. 테스트는 구현 세부사항보다 핵심 규칙과 경계를 검증한다.

### 13.1 핵심 단위 테스트

- slug 정규화, 한글/비라틴 문자, 빈 제목
- canonical 충돌과 안정적인 page ID suffix
- 제목 변경 시 alias 전환
- 공개/비공개 상태 전이
- 갱신 시각과 실패 backoff
- Notion page link 추출과 내부 경로 변환

### 13.2 외부 경계 테스트

- Notion cursor pagination과 중첩 block 수집
- `429`, timeout, `5xx`, 권한 오류 분류
- 설정 data source 파싱과 필수 row 검증
- Flyway migration과 Exposed mapping 일치
- PostgreSQL unique/foreign key 및 route 전이 원자성

### 13.3 웹 경계 테스트

- `/`, canonical, alias `301`, 미존재 `404`
- 비공개 page 비노출
- stale snapshot 제공과 갱신 요청
- 지원/미지원 block HTML 렌더링
- Actuator probe

### 13.4 통합 smoke 테스트

- 빈 DB migration 및 애플리케이션 시작
- 설정 bootstrap 후 root 렌더링
- 공개 page 저장 후 조회
- 제목 변경 후 과거 경로 `301`
- 공개 취소 후 기존 경로 비노출

테스트에서 실제 PostgreSQL 동작을 검증할 때 H2로 대체하지 않는다. Testcontainers PostgreSQL을 사용한다.

## 14. 설정

필수 환경변수:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
NOTION_TOKEN
NOTION_SETTINGS_DATA_SOURCE_ID
NOTION_API_VERSION
BLOG_BASE_URL
SPRING_PROFILES_ACTIVE
```

Secret:

- DB username/password
- Notion token

일반 환경설정:

- JDBC URL
- 설정 data source ID
- Notion API version
- base URL
- Spring profile

## 15. 이미지와 외부 하네스 계약

```text
Notion-Blog repository
├── application source
├── Gradle build/configuration
├── Flyway migrations
├── Dockerfile
└── CI workflow

External harness repository
├── Helm/Kubernetes manifests
├── replica and scheduler activation policy
├── Service/Ingress/TLS
├── Secret/ConfigMap wiring
└── PostgreSQL provisioning
```

- 이 저장소에는 Helm chart, Kubernetes manifest, 배포 하네스 설정을 두지 않는다.
- 이 저장소는 외부 하네스가 사용할 단일 OCI image와 런타임 환경변수 계약만 제공한다.
- 별도 worker image나 migration image를 만들지 않는다. Flyway가 애플리케이션 시작 시 migration을 수행한다.
- 애플리케이션은 `8080` 포트와 `/actuator/health/liveness`, `/actuator/health/readiness` endpoint를 제공한다.
- 이미지 build stage는 JDK 25, runtime stage는 Java 25 runtime을 사용한다.
- Notion API 장애는 readiness를 내리지 않는다.
- replica, probe 연결, Secret 주입, 네트워크와 보안 정책은 외부 하네스가 소유한다.

## 16. 구현 순서와 완료 조건

1. Gradle/JDK/Spring/Exposed 최소 호환성 테스트
2. Flyway와 PostgreSQL persistence 경계
3. domain 규칙
4. Notion HTTP/매핑 경계
5. 동기화 application service
6. MVC/Thymeleaf 렌더링
7. scheduler와 stale-while-refresh
8. Docker image와 런타임 설정
9. 전체 테스트와 smoke 검증

완료 조건:

- 모든 신규 기능이 테스트보다 먼저 구현되지 않았다.
- 전체 Gradle 테스트가 통과한다.
- 애플리케이션이 JDK 25 toolchain으로 build된다.
- Docker 이미지가 생성된다.
- 저장소에 Helm/Kubernetes/하네스 manifest가 남지 않는다.
- 기존 제품 핵심 동작이 신규 테스트로 보존된다.
- Node/Next.js/Prisma/worker 런타임 의존성이 최종 결과에 남지 않는다.
