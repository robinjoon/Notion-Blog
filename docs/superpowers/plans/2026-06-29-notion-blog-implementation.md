# Notion-Blog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Notion-Blog MVP: a Next.js + TypeScript + Prisma application that renders a Notion root page as a public blog, lazily collects linked Notion pages, syncs cached snapshots through a separate worker, and ships with a k3s Helm chart.

**Architecture:** The web app serves cached page snapshots from PostgreSQL and performs limited on-demand collection only when an uncached internal Notion page link is requested. A separate sync worker refreshes settings and page snapshots on simple per-target intervals. Shared domain modules handle settings parsing, slug generation, refresh policy, Notion link resolution, route state, and block snapshot rendering.

**Tech Stack:** Next.js App Router, TypeScript, pnpm, Prisma, PostgreSQL, @notionhq/client, Vitest, React Testing Library, Docker, Helm.

## Global Constraints

- Project name: `Notion-Blog`.
- Package manager: `pnpm`.
- Runtime target: Node.js 24 LTS or newer. Production Docker images use the Node 24 LTS line.
- Helm target: Helm v4.2.2 or newer.
- Normal content pages are public only when Notion `public_url` is present.
- The settings database is private and accessed only through the Notion integration.
- The settings database MVP keys are `rootPage`, `header`, `footer`, and `head`.
- The root page from settings renders at `/`.
- Do not run a separate root page graph crawler in the MVP.
- Convert explicit Notion page links found during rendering into internal blog links.
- Lazily collect an uncached linked page when a visitor accesses it.
- Use title-based slugs for non-root pages.
- Preserve old slugs as direct 301 redirects to the current canonical slug.
- Use simple refresh intervals: settings about 1 minute, normal pages about 10-15 minutes.
- Keep the Helm chart in `deploy/helm/notion-blog`.
- Do not create PostgreSQL from the Helm chart; inject `DATABASE_URL` through an existing Kubernetes Secret.
- Do not implement OAuth, admin UI, multi-site support, page-level overrides, or public Notion URL scraping in the MVP.

---

## File Structure

Create this structure as tasks progress:

```text
.
├── .dockerignore
├── .env.example
├── Dockerfile
├── next.config.ts
├── package.json
├── pnpm-lock.yaml
├── prisma.config.ts
├── public/
│   └── .gitkeep
├── prisma/
│   └── schema.prisma
├── src/
│   ├── app/
│   │   ├── [slug]/page.tsx
│   │   ├── globals.css
│   │   ├── layout.tsx
│   │   ├── not-found.tsx
│   │   └── page.tsx
│   ├── components/notion/
│   │   ├── NotionBlockRenderer.tsx
│   │   ├── NotionPage.tsx
│   │   └── rich-text.tsx
│   ├── domain/
│   │   ├── notion-link.ts
│   │   ├── refresh-policy.ts
│   │   ├── settings.ts
│   │   └── slug.ts
│   ├── notion/
│   │   ├── block-collector.ts
│   │   ├── gateway.ts
│   │   └── page-mapper.ts
│   ├── generated/
│   │   └── prisma/
│   │       └── client.ts
│   ├── server/
│   │   ├── db.ts
│   │   ├── page-service.ts
│   │   ├── repositories.ts
│   │   └── settings-service.ts
│   └── worker/
│       ├── index.ts
│       └── sync-service.ts
├── tests/
│   ├── domain/
│   ├── notion/
│   ├── renderer/
│   ├── server/
│   └── worker/
├── deploy/helm/notion-blog/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── _helpers.tpl
│       ├── ingress.yaml
│       ├── migration-job.yaml
│       ├── service.yaml
│       ├── web-deployment.yaml
│       └── worker-deployment.yaml
└── vitest.config.ts
```

## Interfaces Shared Across Tasks

These are the stable shapes neighboring tasks rely on:

```ts
export type RefreshTargetKind = "settings" | "page";

export interface RefreshTarget {
  targetKind: RefreshTargetKind;
  targetId: string;
  failureCount: number;
  lastSyncedAt: Date | null;
}

export interface SiteHeadSettings {
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

export interface ParsedSettings {
  rootPageId: string;
  headerPageId?: string;
  footerPageId?: string;
  head: SiteHeadSettings;
}

export interface PageMetadata {
  pageId: string;
  title: string;
  notionUrl: string;
  publicUrl: string | null;
  lastEditedTime: string;
}

export interface PageSnapshot {
  pageId: string;
  blocks: NotionBlockSnapshot[];
  capturedAt: string;
  notionLastEditedTime: string;
}

export interface NotionBlockSnapshot {
  id: string;
  type: string;
  hasChildren: boolean;
  data: Record<string, unknown>;
  children: NotionBlockSnapshot[];
}
```

### Task 1: Project Scaffold And Tooling

**Files:**
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Create: `tsconfig.json`
- Create: `next-env.d.ts`
- Create: `next.config.ts`
- Create: `vitest.config.ts`
- Create: `.env.example`
- Create: `src/app/layout.tsx`
- Create: `src/app/page.tsx`
- Create: `src/app/not-found.tsx`
- Create: `src/app/globals.css`
- Create: `public/.gitkeep`
- Create: `tests/smoke/scaffold.test.ts`

**Interfaces:**
- Produces: package scripts: `dev`, `build`, `start`, `test`, `test:run`, `typecheck`, `worker`, `db:generate`, `db:migrate`.

- [ ] **Step 1: Create package manifest**

Create `package.json`:

```json
{
  "name": "notion-blog",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "packageManager": "pnpm@11.9.0",
  "engines": {
    "node": ">=24.0.0"
  },
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "typecheck": "tsc --noEmit",
    "test": "vitest",
    "test:run": "vitest run",
    "worker": "tsx src/worker/index.ts",
    "db:generate": "prisma generate",
    "db:migrate": "prisma migrate deploy"
  },
  "dependencies": {
    "@notionhq/client": "^5.22.0",
    "@prisma/adapter-pg": "^7.8.0",
    "@prisma/client": "^7.8.0",
    "dotenv": "^17.4.2",
    "next": "^16.2.9",
    "pg": "^8.22.0",
    "prisma": "^7.8.0",
    "react": "^19.2.7",
    "react-dom": "^19.2.7",
    "tsx": "^4.22.4",
    "zod": "^4.4.3"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.9.1",
    "@testing-library/react": "^16.3.2",
    "@types/node": "^24.13.2",
    "@types/react": "^19.2.17",
    "@types/react-dom": "^19.2.3",
    "@vitejs/plugin-react": "^6.0.3",
    "jsdom": "^29.1.1",
    "typescript": "~5.9.3",
    "vite": "^8.1.0",
    "vitest": "^4.1.9"
  }
}
```

- [ ] **Step 2: Create pnpm workspace approvals**

Create `pnpm-workspace.yaml`:

```yaml
allowBuilds:
  '@prisma/engines': true
  esbuild: true
  prisma: true
  sharp: true
```

- [ ] **Step 3: Install dependencies**

Run:

```bash
pnpm install
```

Expected: exit 0 and a new `pnpm-lock.yaml`.

- [ ] **Step 4: Add TypeScript and Next config**

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": [
      "dom",
      "dom.iterable",
      "ES2022"
    ],
    "allowJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "react-jsx",
    "incremental": true,
    "paths": {
      "@/*": [
        "./src/*"
      ]
    },
    "plugins": [
      {
        "name": "next"
      }
    ]
  },
  "include": [
    "next-env.d.ts",
    "**/*.ts",
    "**/*.tsx",
    ".next/types/**/*.ts",
    ".next/dev/types/**/*.ts"
  ],
  "exclude": [
    "node_modules"
  ]
}
```

Create `next.config.ts`:

```ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone"
};

export default nextConfig;
```

Create `vitest.config.ts`:

```ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    include: ["tests/**/*.test.ts", "tests/**/*.test.tsx"]
  },
  resolve: {
    alias: {
      "@": new URL("./src", import.meta.url).pathname
    }
  }
});
```

- [ ] **Step 5: Add environment example**

Create `.env.example`:

```dotenv
DATABASE_URL="postgresql://notion_blog:notion_blog@localhost:5432/notion_blog?schema=public"
NOTION_TOKEN="secret_xxx"
SETTINGS_DATABASE_ID="00000000000000000000000000000000"
```

- [ ] **Step 6: Add minimal app shell**

Create `src/app/layout.tsx`:

```tsx
import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Notion-Blog",
  description: "A self-hosted Notion blog"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
```

Create `src/app/page.tsx`:

```tsx
export default function HomePage() {
  return <main className="notion-page">Notion-Blog</main>;
}
```

Create `src/app/not-found.tsx`:

```tsx
export default function NotFoundPage() {
  return <main className="notion-page">Not found</main>;
}
```

Create `src/app/globals.css`:

```css
:root {
  color: #37352f;
  background: #ffffff;
  font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

.notion-page {
  width: min(100% - 32px, 900px);
  margin: 0 auto;
  padding: 48px 0;
}
```

- [ ] **Step 7: Add public directory placeholder**

Create `public/.gitkeep` as an empty file so Docker builds can copy the `public` directory consistently.

- [ ] **Step 8: Add scaffold smoke test**

Create `tests/smoke/scaffold.test.ts`:

```ts
import { describe, expect, it } from "vitest";

describe("project scaffold", () => {
  it("uses the expected package name and Node runtime floor", async () => {
    const manifest = await import("../../package.json");
    expect(manifest.default.name).toBe("notion-blog");
    expect(manifest.default.engines.node).toBe(">=24.0.0");
  });
});
```

- [ ] **Step 9: Verify scaffold**

Run:

```bash
pnpm test:run tests/smoke/scaffold.test.ts
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 10: Commit**

```bash
git add package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.json next-env.d.ts next.config.ts vitest.config.ts .env.example src/app public/.gitkeep tests/smoke/scaffold.test.ts
git commit -m "chore: scaffold notion blog app"
```

### Task 2: Prisma Schema And Database Model

**Files:**
- Create: `prisma/schema.prisma`
- Create: `prisma.config.ts`
- Create: `src/server/db.ts`
- Create: `tests/server/prisma-schema.test.ts`

**Interfaces:**
- Produces Prisma models: `SiteSettings`, `NotionPage`, `PageRoute`, `SlugAlias`, `PageSnapshot`, `RefreshTarget`, `SyncRun`.
- Produces enums: `SlugAliasStatus`, `RefreshTargetKind`, `SyncRunStatus`.
- Produces: `src/server/db.ts` export `prisma: PrismaClient`.

- [ ] **Step 1: Write schema validation test**

Create `tests/server/prisma-schema.test.ts`:

```ts
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Prisma schema", () => {
  const schema = readFileSync("prisma/schema.prisma", "utf8");
  const config = readFileSync("prisma.config.ts", "utf8");
  const dbClient = readFileSync("src/server/db.ts", "utf8");

  it("uses the Prisma 7 generated client", () => {
    expect(schema).toContain("provider = \"prisma-client\"");
    expect(schema).toContain("output   = \"../src/generated/prisma\"");
    expect(schema).not.toMatch(/url\s*=\s*env\("DATABASE_URL"\)/);
    expect(config).toContain("defineConfig");
    expect(config).toContain("env(\"DATABASE_URL\")");
    expect(dbClient).toContain("import { PrismaClient } from \"../generated/prisma/client\"");
    expect(dbClient).not.toContain("from \"@prisma/client\"");
    expect(dbClient).toContain("import { PrismaPg } from \"@prisma/adapter-pg\"");
    expect(dbClient).toContain("adapter: new PrismaPg({ connectionString })");
  });

  it("defines publishing state models", () => {
    expect(schema).toContain("model SiteSettings");
    expect(schema).toContain("model NotionPage");
    expect(schema).toContain("model PageRoute");
    expect(schema).toContain("model SlugAlias");
    expect(schema).toContain("model PageSnapshot");
    expect(schema).toContain("model RefreshTarget");
    expect(schema).toContain("model SyncRun");
  });

  it("models settings as an explicit singleton snapshot", () => {
    expect(schema).toMatch(/settingsDatabaseId\s+String\s+@unique/);
    expect(schema).toMatch(/rootPageId\s+String/);
    expect(schema).toMatch(/headerPageId\s+String\?/);
    expect(schema).toMatch(/footerPageId\s+String\?/);
    expect(schema).toMatch(/headJson\s+Json/);
    expect(schema).not.toContain("settingsJson");
    expect(schema).not.toContain("sourceId");
  });

  it("derives visibility and root routing instead of storing duplicate booleans", () => {
    expect(schema).toContain("publicUrl      String?");
    expect(schema).not.toContain("isPublic");
    expect(schema).not.toContain("isRoot");
  });

  it("keeps canonical slugs and aliases unique", () => {
    expect(schema).toContain("canonicalSlug String     @unique");
    expect(schema).toContain("slug      String          @unique");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test:run tests/server/prisma-schema.test.ts
```

Expected: FAIL because `prisma/schema.prisma` and `prisma.config.ts` do not exist.

- [ ] **Step 3: Create Prisma schema and config**

Create `prisma/schema.prisma`:

```prisma
generator client {
  provider = "prisma-client"
  output   = "../src/generated/prisma"
}

datasource db {
  provider = "postgresql"
}
```

Create `prisma.config.ts`:

```ts
import "dotenv/config";
import { defineConfig, env } from "prisma/config";

export default defineConfig({
  schema: "prisma/schema.prisma",
  migrations: {
    path: "prisma/migrations"
  },
  datasource: {
    url: env("DATABASE_URL")
  }
});
```

Append the enums and models below the datasource block in `prisma/schema.prisma`:

```prisma

enum SlugAliasStatus {
  ACTIVE
  INACTIVE
  CONFLICTED
}

enum RefreshTargetKind {
  SETTINGS
  PAGE
}

enum SyncRunStatus {
  SUCCESS
  FAILED
  SKIPPED
}

model SiteSettings {
  id                 String   @id @default(cuid())
  settingsDatabaseId String   @unique
  rootPageId         String
  headerPageId       String?
  footerPageId       String?
  headJson           Json
  lastSyncedAt       DateTime?
  createdAt          DateTime @default(now())
  updatedAt          DateTime @updatedAt
}

model NotionPage {
  pageId         String        @id
  title          String
  notionUrl      String
  publicUrl      String?
  lastEditedTime DateTime?
  lastSyncedAt   DateTime?
  createdAt      DateTime      @default(now())
  updatedAt      DateTime      @updatedAt
  route          PageRoute?
  aliases        SlugAlias[]
  snapshot       PageSnapshot?
}

model PageRoute {
  pageId        String     @id
  canonicalSlug String     @unique
  isActive      Boolean    @default(true)
  createdAt     DateTime   @default(now())
  updatedAt     DateTime   @updatedAt
  page          NotionPage @relation(fields: [pageId], references: [pageId], onDelete: Cascade)
}

model SlugAlias {
  id        String          @id @default(cuid())
  pageId    String
  slug      String          @unique
  status    SlugAliasStatus @default(ACTIVE)
  createdAt DateTime        @default(now())
  updatedAt DateTime        @updatedAt
  page      NotionPage      @relation(fields: [pageId], references: [pageId], onDelete: Cascade)

  @@index([pageId])
}

model PageSnapshot {
  pageId               String     @id
  snapshotJson          Json
  notionLastEditedTime  DateTime
  capturedAt            DateTime   @default(now())
  page                  NotionPage @relation(fields: [pageId], references: [pageId], onDelete: Cascade)
}

model RefreshTarget {
  targetKind    RefreshTargetKind
  targetId      String
  nextRefreshAt DateTime
  lastSyncedAt  DateTime?
  failureCount  Int               @default(0)
  lastError     String?
  lockedAt      DateTime?
  lockedBy      String?
  createdAt     DateTime          @default(now())
  updatedAt     DateTime          @updatedAt

  @@id([targetKind, targetId])
  @@index([nextRefreshAt])
  @@index([lockedAt])
}

model SyncRun {
  id         String            @id @default(cuid())
  targetKind RefreshTargetKind
  targetId   String
  status     SyncRunStatus
  startedAt  DateTime
  finishedAt DateTime?
  error      String?
}
```

- [ ] **Step 4: Add Prisma client singleton**

Create `src/server/db.ts`:

```ts
import { PrismaPg } from "@prisma/adapter-pg";
import { PrismaClient } from "../generated/prisma/client";

const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient };

function createPrismaClient() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) {
    throw new Error("DATABASE_URL is required");
  }

  return new PrismaClient({
    adapter: new PrismaPg({ connectionString })
  });
}

export const prisma = globalForPrisma.prisma ?? createPrismaClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.prisma = prisma;
}
```

- [ ] **Step 5: Verify schema and generated client**

Run:

```bash
DATABASE_URL="postgresql://notion_blog:notion_blog@localhost:5432/notion_blog?schema=public" pnpm db:generate
pnpm test:run tests/server/prisma-schema.test.ts
pnpm typecheck
```

Expected: all commands exit 0 and `src/generated/prisma/client.ts` exists.

- [ ] **Step 6: Commit**

```bash
git add prisma/schema.prisma prisma.config.ts src/server/db.ts tests/server/prisma-schema.test.ts
git commit -m "feat: add publishing database schema"
```

### Task 3: Domain Utilities

**Files:**
- Create: `src/domain/slug.ts`
- Create: `src/domain/settings.ts`
- Create: `src/domain/refresh-policy.ts`
- Create: `src/domain/notion-link.ts`
- Create: `tests/domain/slug.test.ts`
- Create: `tests/domain/settings.test.ts`
- Create: `tests/domain/refresh-policy.test.ts`
- Create: `tests/domain/notion-link.test.ts`

**Interfaces:**
- Produces: `createSlug(title: string, pageId?: string): string`
- Produces: `createUniqueSlug(base: string, exists: (slug: string) => boolean, pageId: string): string`
- Produces: `parseSettingsRows(rows: SettingsRow[]): ParsedSettings`
- Produces: `simpleRefreshPolicy(target: RefreshTarget, now: Date): Date`
- Produces: `parseNotionPageReference(input: string): { pageId: string } | null`

- [ ] **Step 1: Add failing slug tests**

Create `tests/domain/slug.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { createSlug, createUniqueSlug } from "@/domain/slug";

describe("slug domain", () => {
  it("keeps Korean text readable", () => {
    expect(createSlug("첫 번째 글")).toBe("첫-번째-글");
  });

  it("normalizes latin punctuation", () => {
    expect(createSlug("Next.js Cache Notes!")).toBe("next-js-cache-notes");
  });

  it("falls back to page id when title has no slug characters", () => {
    expect(createSlug("!!!", "1234567890abcdef")).toBe("page-12345678");
  });

  it("adds a stable suffix when slug already exists", () => {
    const taken = new Set(["hello"]);
    expect(createUniqueSlug("hello", (slug) => taken.has(slug), "abcdef123456")).toBe("hello-abcdef12");
  });
});
```

- [ ] **Step 2: Add failing settings tests**

Create `tests/domain/settings.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { parseSettingsRows, type SettingsRow } from "@/domain/settings";

describe("settings parser", () => {
  it("parses rootPage, header, footer, and head rows", () => {
    const rows: SettingsRow[] = [
      { key: "rootPage", kind: "page", enabled: true, page: "https://www.notion.so/Test-0123456789abcdef0123456789abcdef", data: "" },
      { key: "header", kind: "blocks", enabled: true, page: "11111111111111111111111111111111", data: "" },
      { key: "footer", kind: "blocks", enabled: true, page: "22222222222222222222222222222222", data: "" },
      { key: "head", kind: "head", enabled: true, page: "", data: "{\"siteName\":\"Notion-Blog\",\"defaultTitle\":\"Blog\"}" }
    ];

    expect(parseSettingsRows(rows)).toEqual({
      rootPageId: "0123456789abcdef0123456789abcdef",
      headerPageId: "11111111111111111111111111111111",
      footerPageId: "22222222222222222222222222222222",
      head: { siteName: "Notion-Blog", defaultTitle: "Blog" }
    });
  });

  it("rejects missing rootPage", () => {
    expect(() => parseSettingsRows([])).toThrow("settings rootPage is required");
  });
});
```

- [ ] **Step 3: Add failing refresh policy tests**

Create `tests/domain/refresh-policy.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";

describe("simple refresh policy", () => {
  const now = new Date("2026-06-29T00:00:00.000Z");

  it("refreshes settings after one minute", () => {
    expect(simpleRefreshPolicy({ targetKind: "settings", targetId: "settings", failureCount: 0, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:01:00.000Z");
  });

  it("refreshes pages after fifteen minutes", () => {
    expect(simpleRefreshPolicy({ targetKind: "page", targetId: "page-a", failureCount: 0, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:15:00.000Z");
  });

  it("backs off failed targets", () => {
    expect(simpleRefreshPolicy({ targetKind: "page", targetId: "page-a", failureCount: 2, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:20:00.000Z");
  });
});
```

- [ ] **Step 4: Add failing Notion link tests**

Create `tests/domain/notion-link.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { parseNotionPageReference } from "@/domain/notion-link";

describe("Notion link parser", () => {
  it("extracts page id from Notion URL", () => {
    expect(parseNotionPageReference("https://www.notion.so/My-Page-0123456789abcdef0123456789abcdef")).toEqual({
      pageId: "0123456789abcdef0123456789abcdef"
    });
  });

  it("extracts page id from a raw id", () => {
    expect(parseNotionPageReference("0123456789abcdef0123456789abcdef")).toEqual({
      pageId: "0123456789abcdef0123456789abcdef"
    });
  });

  it("ignores non-Notion URLs", () => {
    expect(parseNotionPageReference("https://example.com/post")).toBeNull();
  });
});
```

- [ ] **Step 5: Run tests to verify they fail**

Run:

```bash
pnpm test:run tests/domain
```

Expected: FAIL with unresolved imports from `src/domain/*`.

- [ ] **Step 6: Implement domain modules**

Create `src/domain/slug.ts`, `src/domain/settings.ts`, `src/domain/refresh-policy.ts`, and `src/domain/notion-link.ts` matching the interfaces above. Use `zod` in `settings.ts` to parse `head` JSON and throw exact messages:

```ts
throw new Error("settings rootPage is required");
throw new Error(`settings ${key} has invalid JSON`);
```

Use `parseNotionPageReference()` inside `parseSettingsRows()` for page IDs and Notion URLs.

- [ ] **Step 7: Verify domain modules**

Run:

```bash
pnpm test:run tests/domain
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 8: Commit**

```bash
git add src/domain tests/domain
git commit -m "feat: add core blog domain utilities"
```

### Task 4: Notion Gateway And Snapshot Collector

**Files:**
- Create: `src/notion/gateway.ts`
- Create: `src/notion/page-mapper.ts`
- Create: `src/notion/block-collector.ts`
- Create: `tests/notion/page-mapper.test.ts`
- Create: `tests/notion/block-collector.test.ts`

**Interfaces:**
- Consumes: `PageMetadata`, `PageSnapshot`, `NotionBlockSnapshot`.
- Produces: `NotionGateway` interface.
- Produces: `createNotionGateway(token: string): NotionGateway`.
- Produces: `collectPageSnapshot(gateway: NotionGateway, pageId: string, metadata: PageMetadata): Promise<PageSnapshot>`.

- [ ] **Step 1: Add failing page mapper test**

Create `tests/notion/page-mapper.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { mapNotionPageMetadata } from "@/notion/page-mapper";

describe("Notion page mapper", () => {
  it("maps public_url and title from a Notion page response", () => {
    const metadata = mapNotionPageMetadata({
      id: "01234567-89ab-cdef-0123-456789abcdef",
      url: "https://www.notion.so/Test-0123456789abcdef0123456789abcdef",
      public_url: "https://site.notion.site/Test-0123456789abcdef0123456789abcdef",
      last_edited_time: "2026-06-29T00:00:00.000Z",
      properties: {
        title: {
          type: "title",
          title: [{ plain_text: "Hello Notion" }]
        }
      }
    });

    expect(metadata).toEqual({
      pageId: "0123456789abcdef0123456789abcdef",
      title: "Hello Notion",
      notionUrl: "https://www.notion.so/Test-0123456789abcdef0123456789abcdef",
      publicUrl: "https://site.notion.site/Test-0123456789abcdef0123456789abcdef",
      lastEditedTime: "2026-06-29T00:00:00.000Z"
    });
  });
});
```

- [ ] **Step 2: Add failing block collector test**

Create `tests/notion/block-collector.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { collectPageSnapshot, type NotionGateway } from "@/notion/block-collector";

describe("block collector", () => {
  it("recursively collects child blocks", async () => {
    const gateway: NotionGateway = {
      retrievePage: async () => {
        throw new Error("not used");
      },
      retrieveBlockChildren: async (blockId: string) => {
        if (blockId === "page-a") {
          return [{ id: "paragraph-a", type: "paragraph", has_children: true, paragraph: { rich_text: [{ plain_text: "Parent" }] } }];
        }
        if (blockId === "paragraph-a") {
          return [{ id: "paragraph-b", type: "paragraph", has_children: false, paragraph: { rich_text: [{ plain_text: "Child" }] } }];
        }
        return [];
      },
      querySettingsDatabase: async () => []
    };

    const snapshot = await collectPageSnapshot(gateway, "page-a", {
      pageId: "page-a",
      title: "Page A",
      notionUrl: "https://www.notion.so/page-a",
      publicUrl: "https://site.notion.site/page-a",
      lastEditedTime: "2026-06-29T00:00:00.000Z"
    });

    expect(snapshot.blocks[0]).toMatchObject({
      id: "paragraph-a",
      type: "paragraph",
      hasChildren: true,
      children: [{ id: "paragraph-b", type: "paragraph" }]
    });
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
pnpm test:run tests/notion
```

Expected: FAIL with unresolved imports from `src/notion/*`.

- [ ] **Step 4: Implement Notion gateway and collectors**

Create:

```ts
// src/notion/gateway.ts
import { Client } from "@notionhq/client";

export interface NotionGateway {
  retrievePage(pageId: string): Promise<unknown>;
  retrieveBlockChildren(blockId: string, startCursor?: string): Promise<unknown[]>;
  querySettingsDatabase(databaseId: string): Promise<unknown[]>;
}

export function createNotionGateway(token: string): NotionGateway {
  const client = new Client({ auth: token });
  return {
    async retrievePage(pageId) {
      return client.pages.retrieve({ page_id: pageId });
    },
    async retrieveBlockChildren(blockId, startCursor) {
      const results: unknown[] = [];
      let cursor: string | undefined = startCursor;
      do {
        const response = await client.blocks.children.list({ block_id: blockId, start_cursor: cursor });
        results.push(...response.results);
        cursor = response.has_more ? response.next_cursor ?? undefined : undefined;
      } while (cursor);
      return results;
    },
    async querySettingsDatabase(databaseId) {
      const response = await client.databases.query({ database_id: databaseId });
      return response.results;
    }
  };
}
```

Implement `mapNotionPageMetadata()` in `src/notion/page-mapper.ts` and `collectPageSnapshot()` in `src/notion/block-collector.ts`. Keep raw block payload under `data[type]` so renderers can choose supported fields without losing unknown block data.

- [ ] **Step 5: Verify Notion adapter**

Run:

```bash
pnpm test:run tests/notion
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/notion tests/notion
git commit -m "feat: add notion snapshot collection"
```

### Task 5: Persistence Repositories And Sync Services

**Files:**
- Create: `src/server/repositories.ts`
- Create: `src/server/settings-service.ts`
- Create: `src/server/page-service.ts`
- Create: `src/worker/sync-service.ts`
- Create: `tests/server/repositories.test.ts`
- Create: `tests/server/settings-service.test.ts`
- Create: `tests/worker/sync-service.test.ts`

**Interfaces:**
- Consumes: Prisma models from Task 2, domain utilities from Task 3, Notion gateway from Task 4.
- Produces: `upsertPageSnapshot(input): Promise<void>`.
- Produces: `resolveRoute(slug: string): Promise<RouteResolution>`.
- Produces: `syncSettings(): Promise<ParsedSettings>`.
- Produces: `syncPage(pageId: string): Promise<void>`.
- Produces: `claimDueRefreshTargets(now: Date, workerId: string, limit: number): Promise<RefreshTarget[]>`.

- [ ] **Step 1: Add repository tests**

Create `tests/server/repositories.test.ts` with mocked Prisma methods. Verify:

```ts
expect(resolveRouteResult).toEqual({ kind: "redirect", destination: "/current-slug" });
expect(rootRouteResult).toEqual({ kind: "page", pageId: "root-page", slug: "/" });
```

Use fake repository dependencies rather than a live PostgreSQL database.

- [ ] **Step 2: Add settings service tests**

Create `tests/server/settings-service.test.ts`. Mock `NotionGateway.querySettingsDatabase()` to return four settings rows and assert `syncSettings()` stores one `SiteSettings` snapshot with `rootPageId`, `headerPageId`, `footerPageId`, and `head`.

- [ ] **Step 3: Add worker service tests**

Create `tests/worker/sync-service.test.ts`. Mock the repository and gateway so:

```ts
await syncPage("page-a");
expect(repository.upsertPageSnapshot).toHaveBeenCalledWith(expect.objectContaining({ pageId: "page-a" }));
expect(repository.markPagePrivate).not.toHaveBeenCalled();
```

Add a second test where `publicUrl` is `null` and assert `markPagePrivate("page-a")` is called and snapshot collection is not called.

- [ ] **Step 4: Run tests to verify they fail**

Run:

```bash
pnpm test:run tests/server tests/worker
```

Expected: FAIL with unresolved service and repository imports.

- [ ] **Step 5: Implement repositories and sync services**

Implement repository functions with dependency injection:

```ts
export interface BlogRepository {
  upsertPageSnapshot(input: UpsertPageSnapshotInput): Promise<void>;
  markPagePrivate(pageId: string): Promise<void>;
  resolveRoute(slug: string): Promise<RouteResolution>;
  upsertRefreshTarget(target: RefreshTarget): Promise<void>;
  claimDueRefreshTargets(now: Date, workerId: string, limit: number): Promise<RefreshTarget[]>;
}
```

Use Prisma transactions for page metadata, route, alias, and snapshot writes. Use root route `/` for `rootPageId`. Use `lockedAt` and `lockedBy` when claiming refresh targets.

- [ ] **Step 6: Verify services**

Run:

```bash
pnpm test:run tests/server tests/worker
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/server src/worker/sync-service.ts tests/server tests/worker
git commit -m "feat: add sync persistence services"
```

### Task 6: Notion Renderer And Link Rewriting

**Files:**
- Create: `src/components/notion/rich-text.tsx`
- Create: `src/components/notion/NotionBlockRenderer.tsx`
- Create: `src/components/notion/NotionPage.tsx`
- Create: `tests/renderer/rich-text.test.tsx`
- Create: `tests/renderer/notion-renderer.test.tsx`

**Interfaces:**
- Consumes: `NotionBlockSnapshot`.
- Produces: `NotionPage({ blocks, title }: { blocks: NotionBlockSnapshot[]; title: string })`.
- Produces: `rewriteNotionHref(href: string): string`.

- [ ] **Step 1: Add renderer tests**

Create `tests/renderer/notion-renderer.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NotionPage } from "@/components/notion/NotionPage";

describe("Notion renderer", () => {
  it("renders paragraphs and headings", () => {
    render(<NotionPage title="Hello" blocks={[
      { id: "h", type: "heading_1", hasChildren: false, data: { rich_text: [{ plain_text: "Title" }] }, children: [] },
      { id: "p", type: "paragraph", hasChildren: false, data: { rich_text: [{ plain_text: "Body" }] }, children: [] }
    ]} />);

    expect(screen.getByRole("heading", { name: "Title" })).toBeInTheDocument();
    expect(screen.getByText("Body")).toBeInTheDocument();
  });

  it("shows fallback for unsupported blocks", () => {
    render(<NotionPage title="Hello" blocks={[
      { id: "x", type: "unknown_block", hasChildren: false, data: {}, children: [] }
    ]} />);

    expect(screen.getByText("Unsupported Notion block: unknown_block")).toBeInTheDocument();
  });
});
```

Create `tests/renderer/rich-text.test.tsx` and assert a Notion page link is rendered as an internal href:

```tsx
expect(anchor.getAttribute("href")).toBe("/notion/0123456789abcdef0123456789abcdef");
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
pnpm test:run tests/renderer
```

Expected: FAIL with unresolved renderer imports.

- [ ] **Step 3: Implement renderer**

Implement block support for paragraph, heading_1, heading_2, heading_3, bulleted_list_item, numbered_list_item, to_do, toggle, quote, callout, divider, code, image, video, file, bookmark, table, column_list, column, and child_page. Render children recursively. Unsupported blocks render:

```tsx
<div className="notion-unsupported">Unsupported Notion block: {block.type}</div>
```

Implement `rewriteNotionHref()` so explicit Notion page URLs become `/notion/:pageId`. This temporary `/notion/:pageId` route is resolved in Task 7 and redirected to the title-based route after collection.

- [ ] **Step 4: Add Notion-like CSS**

Extend `src/app/globals.css` with classes:

```css
.notion-block { margin: 4px 0; line-height: 1.65; }
.notion-heading-1 { font-size: 2rem; margin: 1.4em 0 0.5em; }
.notion-heading-2 { font-size: 1.5rem; margin: 1.2em 0 0.45em; }
.notion-heading-3 { font-size: 1.25rem; margin: 1em 0 0.4em; }
.notion-callout { display: flex; gap: 12px; padding: 16px; background: #f7f6f3; border-radius: 6px; }
.notion-code { padding: 16px; background: #f7f6f3; border-radius: 6px; overflow-x: auto; }
.notion-unsupported { padding: 12px; border: 1px solid #d3d1cb; color: #787774; border-radius: 6px; }
```

- [ ] **Step 5: Verify renderer**

Run:

```bash
pnpm test:run tests/renderer
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/components/notion src/app/globals.css tests/renderer
git commit -m "feat: render notion page snapshots"
```

### Task 7: Web Routes, Metadata, And On-Demand Collection

**Files:**
- Modify: `src/app/layout.tsx`
- Modify: `src/app/page.tsx`
- Create: `src/app/[slug]/page.tsx`
- Create: `src/app/notion/[pageId]/route.ts`
- Create: `tests/server/page-service.test.ts`

**Interfaces:**
- Consumes: `resolveRoute(slug)`, `syncPage(pageId)`, renderer components.
- Produces: `/` root route from `rootPage`.
- Produces: `/:slug` page route.
- Produces: `/notion/:pageId` collection route that redirects to canonical slug or returns 404 for non-public pages.

- [ ] **Step 1: Add page service tests**

Create `tests/server/page-service.test.ts` and assert:

```ts
expect(await service.getRootPage()).toEqual(expect.objectContaining({ slug: "/" }));
expect(await service.getPageBySlug("old-slug")).toEqual({ kind: "redirect", destination: "/new-slug" });
expect(await service.collectLinkedPage("page-a")).toEqual({ kind: "redirect", destination: "/hello" });
expect(await service.collectLinkedPage("private-page")).toEqual({ kind: "notFound" });
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test:run tests/server/page-service.test.ts
```

Expected: FAIL until route-facing page service exists.

- [ ] **Step 3: Implement routes**

Implement:

```tsx
// src/app/page.tsx
import { notFound } from "next/navigation";
import { NotionPage } from "@/components/notion/NotionPage";
import { getPageService } from "@/server/page-service";

export default async function HomePage() {
  const result = await getPageService().getRootPage();
  if (result.kind !== "page") notFound();
  return <NotionPage title={result.page.title} blocks={result.snapshot.blocks} />;
}
```

Implement `src/app/[slug]/page.tsx` with `redirect()` for alias results and `notFound()` for missing/private results.

Implement `src/app/notion/[pageId]/route.ts` as a GET route that calls `collectLinkedPage(pageId)` and redirects to the resulting canonical route.

- [ ] **Step 4: Implement metadata**

Modify `src/app/layout.tsx` to load settings head values through the settings service and apply `language`, default title, description, favicon, OG, Twitter, robots, and custom CSS. Keep `customHeadHtml` disabled unless it is sanitized or emitted from a narrowly controlled allowlist; the MVP stores it but does not render raw HTML.

- [ ] **Step 5: Verify routes**

Run:

```bash
pnpm test:run tests/server/page-service.test.ts tests/renderer
pnpm typecheck
```

Expected: commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/app src/server/page-service.ts tests/server/page-service.test.ts
git commit -m "feat: add blog routes and metadata"
```

### Task 8: Worker Entrypoint

**Files:**
- Create: `src/worker/index.ts`
- Modify: `src/worker/sync-service.ts`
- Create: `tests/worker/worker-loop.test.ts`

**Interfaces:**
- Consumes: `claimDueRefreshTargets()`, `syncSettings()`, `syncPage()`, `simpleRefreshPolicy()`.
- Produces: CLI process run by `pnpm worker`.

- [ ] **Step 1: Add worker loop test**

Create `tests/worker/worker-loop.test.ts`:

```ts
import { describe, expect, it, vi } from "vitest";
import { runWorkerOnce } from "@/worker/index";

describe("worker loop", () => {
  it("syncs settings and page targets", async () => {
    const syncSettings = vi.fn().mockResolvedValue(undefined);
    const syncPage = vi.fn().mockResolvedValue(undefined);
    const repository = {
      claimDueRefreshTargets: vi.fn().mockResolvedValue([
        { targetKind: "settings", targetId: "settings", failureCount: 0, lastSyncedAt: null },
        { targetKind: "page", targetId: "page-a", failureCount: 0, lastSyncedAt: null }
      ]),
      completeRefreshTarget: vi.fn().mockResolvedValue(undefined),
      failRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };

    await runWorkerOnce({ repository, syncSettings, syncPage, workerId: "worker-test", now: new Date("2026-06-29T00:00:00.000Z") });

    expect(syncSettings).toHaveBeenCalledOnce();
    expect(syncPage).toHaveBeenCalledWith("page-a");
    expect(repository.completeRefreshTarget).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test:run tests/worker/worker-loop.test.ts
```

Expected: FAIL until `runWorkerOnce()` exists.

- [ ] **Step 3: Implement worker entrypoint**

Implement `runWorkerOnce()` and a production loop in `src/worker/index.ts`. Use `process.env.WORKER_POLL_INTERVAL_MS ?? "5000"`. On `SIGTERM`, finish the current target then exit.

- [ ] **Step 4: Verify worker**

Run:

```bash
pnpm test:run tests/worker
pnpm typecheck
```

Expected: both commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/worker tests/worker
git commit -m "feat: add sync worker entrypoint"
```

### Task 9: Docker Image And Helm Chart

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `deploy/helm/notion-blog/Chart.yaml`
- Create: `deploy/helm/notion-blog/values.yaml`
- Create: `deploy/helm/notion-blog/templates/_helpers.tpl`
- Create: `deploy/helm/notion-blog/templates/web-deployment.yaml`
- Create: `deploy/helm/notion-blog/templates/worker-deployment.yaml`
- Create: `deploy/helm/notion-blog/templates/migration-job.yaml`
- Create: `deploy/helm/notion-blog/templates/service.yaml`
- Create: `deploy/helm/notion-blog/templates/ingress.yaml`
- Create: `tests/deploy/helm.test.ts`

**Interfaces:**
- Consumes: `pnpm build`, `pnpm worker`, `pnpm db:migrate`.
- Produces: deployable container image and Helm chart.

- [ ] **Step 1: Add Helm template test**

Create `tests/deploy/helm.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

describe("Helm chart", () => {
  it("keeps chart outside app source", () => {
    expect(existsSync("deploy/helm/notion-blog/Chart.yaml")).toBe(true);
  });

  it("references existing secret instead of creating database credentials", () => {
    const values = readFileSync("deploy/helm/notion-blog/values.yaml", "utf8");
    expect(values).toContain("existingSecret:");
    expect(values).not.toContain("postgresql:");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test:run tests/deploy/helm.test.ts
```

Expected: FAIL because chart files do not exist.

- [ ] **Step 3: Add Dockerfile**

Create `Dockerfile`:

```dockerfile
FROM node:24-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

FROM node:24-alpine AS builder
WORKDIR /app
RUN corepack enable
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm db:generate
RUN pnpm build

FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN corepack enable
COPY --from=builder /app/package.json /app/pnpm-lock.yaml ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/prisma.config.ts ./prisma.config.ts
COPY --from=builder /app/src ./src
EXPOSE 3000
CMD ["pnpm", "start"]
```

Create `.dockerignore`:

```text
.git
.next
node_modules
coverage
test-results
playwright-report
.env
.env.*
deploy/helm/**/*.tgz
```

- [ ] **Step 4: Add Helm chart**

Create chart files under `deploy/helm/notion-blog`. The chart must use:

```yaml
env:
  existingSecret: notion-blog-env
```

Both web and worker Deployments must load env with:

```yaml
envFrom:
  - secretRef:
      name: {{ required "env.existingSecret is required" .Values.env.existingSecret | quote }}
```

The migration Job command must run:

```yaml
command: ["pnpm", "db:migrate"]
```

The worker Deployment command must run:

```yaml
command: ["pnpm", "worker"]
```

- [ ] **Step 5: Verify Helm chart**

Run:

```bash
pnpm test:run tests/deploy/helm.test.ts
helm template notion-blog deploy/helm/notion-blog --set image.repository=notion-blog --set image.tag=test
```

Expected: test exits 0 and `helm template` exits 0 with rendered Deployment, Job, Service, and optional Ingress manifests.

- [ ] **Step 6: Commit**

```bash
git add Dockerfile .dockerignore deploy/helm/notion-blog tests/deploy/helm.test.ts
git commit -m "feat: add docker and helm deployment"
```

### Task 10: Final Verification And Project Documentation

**Files:**
- Create: `README.md`
- Create: `docs/notion-settings-schema.md`

**Interfaces:**
- Consumes all previous tasks.
- Produces documented local setup, settings DB schema, test commands, Docker build command, Helm render command.

- [ ] **Step 1: Write documentation**

Create `README.md` with these sections:

```markdown
# Notion-Blog

Notion-Blog renders a Notion root page as a self-hosted public blog.

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

## Local Commands

- `pnpm install`
- `pnpm db:generate`
- `pnpm dev`
- `pnpm worker`
- `pnpm test:run`
- `pnpm typecheck`
- `pnpm build`

## Deployment

The Helm chart lives in `deploy/helm/notion-blog`.
It expects an existing Kubernetes Secret referenced by `env.existingSecret`.
```

Create `docs/notion-settings-schema.md` documenting the `Notion-Blog Settings` database properties and rows exactly as in the design spec.

- [ ] **Step 2: Run full verification**

Run:

```bash
pnpm test:run
pnpm typecheck
pnpm build
helm template notion-blog deploy/helm/notion-blog --set image.repository=notion-blog --set image.tag=test
```

Expected: all commands exit 0.

- [ ] **Step 3: Commit**

```bash
git add README.md docs/notion-settings-schema.md
git commit -m "docs: add setup and settings documentation"
```

## Self-Review Checklist

- Spec coverage:
  - Notion API collection: Tasks 4 and 5.
  - Root page rendering from settings DB: Tasks 3, 5, and 7.
  - Lazy linked page collection: Tasks 3, 6, and 7.
  - `public_url` exposure control: Tasks 4, 5, and 7.
  - Private settings DB with `rootPage`, `header`, `footer`, `head`: Tasks 3, 5, and 10.
  - PostgreSQL cache and route state: Tasks 2 and 5.
  - Title-based slug and alias redirects: Tasks 3, 5, and 7.
  - Notion-like renderer: Task 6.
  - Separate worker: Task 8.
  - Docker and Helm chart: Task 9.
- Type consistency:
  - `RefreshTargetKind` is only `"settings" | "page"` in TypeScript and `SETTINGS | PAGE` in Prisma.
  - `ParsedSettings.rootPageId` is the source for `/`.
  - `SETTINGS_DATABASE_ID` remains the only Notion settings environment ID.
  - Root page selection is read from the settings database, not from a separate environment variable.
- Verification gates:
  - Each task has a failing-test step before implementation.
  - Each task ends with exact commands and a commit.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-29-notion-blog-implementation.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration.

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
