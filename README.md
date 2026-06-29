# Notion-Blog

Notion-Blog renders a Notion root page as a self-hosted public blog.

이 프로젝트는 비공개 Notion 설정 데이터베이스에서 블로그 설정을 읽고, 공개 가능한 Notion 페이지를 수집해 Next.js 블로그로 서빙합니다. 웹 앱과 동기화 worker가 분리되어 있으며, PostgreSQL에는 페이지 스냅샷과 라우트 상태를 캐시합니다.

## Stack

- Next.js App Router
- TypeScript
- Prisma
- PostgreSQL
- Notion API
- Docker
- Helm

## Required Environment

- Node.js 24 LTS or newer
- Helm v4.2.2 or newer
- `DATABASE_URL`
- `NOTION_TOKEN`
- `SETTINGS_DATABASE_ID`

`NOTION_TOKEN`은 설정 데이터베이스와 페이지 수집에 모두 사용합니다. `SETTINGS_DATABASE_ID`는 사이트 설정만 담는 비공개 Notion 데이터베이스 ID이며, 루트 페이지 선택도 이 데이터베이스의 `rootPage` row에서 결정됩니다.

기본 예시는 [.env.example](/Users/imsubin/Documents/Notion-Blog/.env.example) 에 있습니다.

```bash
DATABASE_URL="postgresql://notion_blog:notion_blog@localhost:5432/notion_blog?schema=public"
NOTION_TOKEN="secret_xxx"
SETTINGS_DATABASE_ID="00000000000000000000000000000000"
```

## Local Commands

- `pnpm install`
- `pnpm db:generate`
- `pnpm dev`
- `pnpm worker`
- `pnpm test:run`
- `pnpm typecheck`
- `pnpm build`

권장 순서는 아래와 같습니다.

```bash
pnpm install
pnpm db:generate
pnpm dev
```

worker는 별도 터미널에서 실행합니다.

```bash
pnpm worker
```

검증 명령은 아래 3개를 기준으로 맞춰져 있습니다.

```bash
pnpm test:run
pnpm typecheck
pnpm build
```

## Deployment

The Helm chart lives in `deploy/helm/notion-blog`.
It expects an existing Kubernetes Secret referenced by `env.existingSecret`.

현재 chart는 web Deployment, worker Deployment, migration Job, Service, 선택적 Ingress를 렌더링합니다. 기본 Secret 이름은 `notion-blog-env`이며, 이 Secret 안에 최소한 `DATABASE_URL`, `NOTION_TOKEN`, `SETTINGS_DATABASE_ID`가 있어야 합니다.

Docker 이미지는 루트의 [Dockerfile](/Users/imsubin/Documents/Notion-Blog/Dockerfile) 로 빌드합니다.

```bash
docker build -t notion-blog:local .
```

Helm 렌더링 확인:

```bash
helm template notion-blog deploy/helm/notion-blog \
  --set image.repository=notion-blog \
  --set image.tag=test
```

배포 예시:

```bash
helm upgrade --install notion-blog deploy/helm/notion-blog \
  --set image.repository=<registry>/notion-blog \
  --set image.tag=<tag> \
  --set env.existingSecret=notion-blog-env
```

Ingress가 필요하면 `deploy/helm/notion-blog/values.yaml`의 `ingress.enabled`, `ingress.hosts`, `ingress.tls`를 환경에 맞게 override해서 배포하면 됩니다.

설정 데이터베이스 스키마와 각 row 의미는 [docs/notion-settings-schema.md](/Users/imsubin/Documents/Notion-Blog/docs/notion-settings-schema.md) 에 정리되어 있습니다.
