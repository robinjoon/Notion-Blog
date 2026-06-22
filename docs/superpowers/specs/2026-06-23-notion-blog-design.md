# Notion Blog Design

## Purpose

Build a self-hosted blog application that turns a Notion page graph into a public blog. The product benchmarks the core value of Oopy: using Notion as the authoring surface while serving a polished website from a custom app. The first version should avoid paid third-party blog tooling and run on the user's existing k3s server.

The application is not a Notion public-page scraper. It uses the official Notion API for content collection, caching, route generation, and rendering. Notion public share remains the source of truth for whether a normal content page should be exposed through the blog.

## Product Principles

- Notion remains the content editor and access-control surface.
- The app owns web publishing concerns: cache, routes, redirects, SEO-ready rendering, header, footer, and custom CSS.
- Blog pages are discovered from a configured root Notion page by traversing reachable Notion pages.
- The settings database is private in Notion and is read only by the app through the Notion integration.
- The first version should be small, stable, and extensible rather than algorithmically clever.

## Scope

### Included In MVP

- Notion API based page and block collection.
- Root page graph traversal for discovering blog pages.
- Public-share based exposure control using each page's Notion `public_url`.
- Private settings database for global site settings.
- PostgreSQL backed page cache, route state, snapshots, slug aliases, and refresh state.
- Title-based canonical slug generation.
- Historical slug aliases that redirect directly to the current canonical slug.
- Notion-like rendering for common block types.
- Separate web and sync worker processes.
- Docker and k3s deployment assumptions.

### Excluded From MVP

- Notion public URL scraping.
- OAuth or multi-user account management.
- Admin UI.
- Multi-site or multi-tenant support.
- Page-level settings overrides.
- Advanced refresh scheduling algorithms.
- Full 100% reproduction of every Notion UI detail.
- Custom per-page slug overrides.

## Architecture

The system has three runtime roles:

1. Web app
   - Runs Next.js.
   - Serves public blog routes.
   - Reads only PostgreSQL snapshots and route tables during normal page rendering.
   - Does not call the Notion API during visitor requests.

2. Sync worker
   - Runs as a separate process or k3s Deployment from the same codebase.
   - Reads due refresh targets from PostgreSQL.
   - Calls the Notion API with rate limiting.
   - Updates settings, page metadata, block snapshots, route state, slug aliases, and refresh state.

3. Migration job
   - Runs Prisma migrations before or during deployment.
   - Uses the same database schema as web and worker.

PostgreSQL is the publishing state store, not just a temporary cache. It records what the app currently knows about each page, how that page maps to a route, which old slugs should redirect, and when each document should be checked again.

## Technology Stack

- Next.js for SSR, routing, metadata, redirects, sitemap generation, and React rendering.
- TypeScript for shared types across web, renderer, sync logic, and Notion adapters.
- Prisma for PostgreSQL schema and migrations.
- PostgreSQL for durable cache, route state, slug aliases, snapshots, and sync metadata.
- Docker image deployed to the user's personal k3s cluster.

## Notion Model

### Content Root

`ROOT_PAGE_ID` points to the first entry page of the blog. The sync worker starts graph discovery from this page.

Pages reachable from the root page are candidate blog pages. Reachability should initially include direct Notion page structures such as child pages and page links that the Notion API exposes in block data. The traversal logic should be isolated behind a graph discovery module so additional edge types can be added later.

### Settings Database

`SETTINGS_DATABASE_ID` points to a private Notion database or page used for global site configuration. It is not discovered from the root page graph and never becomes a blog route.

MVP settings:

- Site title.
- Site description.
- Logo URL or Notion file reference.
- Favicon URL or Notion file reference.
- Header links.
- Footer content.
- Custom CSS.

The settings parser should accept a structured representation that can later grow a page override section, but page overrides are not implemented in the MVP.

### Public Exposure

Normal blog pages must have Notion public share enabled. The app still fetches their content through the Notion API, but it treats Notion public share as the source of truth for public exposure.

Rules:

- If a reachable page has `public_url`, it can be exposed by the blog.
- If `public_url` is missing or becomes null, the page must not be served as public content.
- The settings database must remain private and only shared with the Notion integration.
- The worker should refresh public state before updating or serving stale public assumptions.

## Refresh Model

The app is a cached publisher, not a real-time proxy. Notion updates should appear on the blog after a bounded delay. Each tracked target has its own refresh state.

Core fields:

- `last_synced_at`
- `next_refresh_at`
- `failure_count`
- `last_error`
- `last_edited_time`

Initial refresh policy:

- Settings: short interval, about 1 minute.
- Graph discovery: moderate interval, about 5 minutes.
- Normal pages: moderate interval, about 10 to 15 minutes.
- Failed targets: retry with a simple backoff.

The first implementation should use a simple constant-based policy. The design must keep a `RefreshPolicy` boundary so later versions can use last edited time, traffic, priority, or other heuristics without rewriting the worker.

Recommended interface shape:

```ts
type RefreshTargetKind = "settings" | "graph" | "page";

interface RefreshPolicy {
  nextRefreshAt(target: RefreshTarget, now: Date): Date;
}
```

## Sync Flow

1. The worker loads global settings from the private settings source.
2. The worker refreshes the root graph when the graph target is due.
3. Graph traversal records reachable Notion page IDs as candidate pages.
4. For each due page, the worker calls Notion page retrieval to check title, `public_url`, and `last_edited_time`.
5. If `public_url` is missing, the app marks the page as non-public and stops serving it.
6. If the page is public and `last_edited_time` changed, the worker recursively retrieves block children and stores a new snapshot.
7. The worker computes the title-based canonical slug.
8. If the canonical slug changed, the old slug is inserted as an alias for that page.
9. Any old slug for the page redirects directly to the current canonical slug.
10. The worker updates `next_refresh_at` through the refresh policy.

## Routing And Slugs

The canonical route for a page is generated from the Notion page title.

Rules:

- Slugs are title-based by default.
- Korean and non-Latin titles may remain readable in the slug instead of being transliterated.
- Duplicate current slugs are disambiguated with a short stable page ID suffix.
- When a title changes and the slug changes, the previous canonical slug is stored as an alias.
- If a page title changes multiple times, every old slug redirects directly to the final current canonical slug.
- Alias redirects should use 301 status.
- Canonical slugs take priority over aliases during conflict resolution.
- Alias conflicts should be recorded as inactive or conflicted rather than silently stealing another page's route.

Example:

```text
/hello         -> /hello-world
/hello-worlds -> /hello-world
/hello-world  -> current page
```

## Rendering

The renderer should reproduce Notion pages as closely as practical. The app should feel like a Notion document served as a polished blog, not a heavily redesigned markdown blog.

Priority block support:

- Paragraph.
- Headings.
- Bulleted and numbered lists.
- To-do blocks.
- Toggle blocks.
- Quote blocks.
- Callouts.
- Dividers.
- Code blocks.
- Images.
- Video embeds.
- Files.
- Bookmarks.
- Tables.
- Columns.
- Child page links.

Rendering requirements:

- Preserve nested block structure.
- Preserve rich text annotations where the API provides them.
- Preserve Notion-like spacing, typography, callout colors, code blocks, and content width.
- Show a visible fallback for unsupported blocks rather than silently dropping content.
- Keep renderer modules small enough that individual block renderers can be tested independently.

## Data Model

Initial tables:

### `site_settings`

Stores the latest parsed global settings snapshot.

Important fields:

- `id`
- `settings_json`
- `source_id`
- `last_synced_at`
- `updated_at`

### `notion_pages`

Stores current Notion metadata for each discovered page.

Important fields:

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

Stores current route state.

Important fields:

- `page_id`
- `canonical_slug`
- `is_active`
- `created_at`
- `updated_at`

Constraints:

- `canonical_slug` should be unique among active routes.

### `slug_aliases`

Stores historical slugs for direct redirects to the current canonical route.

Important fields:

- `id`
- `page_id`
- `slug`
- `status`
- `created_at`
- `updated_at`

Constraints:

- Active alias slugs should be unique.
- Alias resolution must check the target page's current canonical slug, not store a redirect chain.

### `page_snapshots`

Stores renderable Notion block trees.

Important fields:

- `page_id`
- `snapshot_json`
- `notion_last_edited_time`
- `captured_at`

### `refresh_targets`

Stores sync scheduling state.

Important fields:

- `target_kind`
- `target_id`
- `next_refresh_at`
- `last_synced_at`
- `failure_count`
- `last_error`
- `locked_at`
- `locked_by`

The worker should claim rows with database-level locking so multiple worker replicas do not process the same target simultaneously.

### `sync_runs`

Stores worker run history for debugging.

Important fields:

- `id`
- `target_kind`
- `target_id`
- `status`
- `started_at`
- `finished_at`
- `error`

## Error Handling

- Visitor requests should prefer serving the latest valid snapshot when a refresh failed.
- If a page becomes non-public, it should stop serving even if an old snapshot exists.
- If no snapshot exists, the route should return 404 or a controlled unavailable page depending on route state.
- Unsupported Notion blocks should render a fallback block with the block type.
- Worker failures should be recorded in `sync_runs` and `refresh_targets.last_error`.
- Repeated worker failures should back off instead of repeatedly consuming API quota.

## Deployment

The k3s deployment should use:

- One web Deployment running the Next.js server.
- One worker Deployment running the sync worker.
- One migration Job for Prisma migrations.
- A Service and Ingress for the web app.
- Kubernetes Secrets for:
  - `DATABASE_URL`
  - `NOTION_TOKEN`
  - `ROOT_PAGE_ID`
  - `SETTINGS_DATABASE_ID`
- Readiness and liveness probes for the web app.
- Graceful shutdown for the worker so in-flight sync jobs can finish or release locks.

## Testing Strategy

Unit tests:

- Slug generation.
- Slug conflict handling.
- Alias resolution.
- Refresh policy.
- Settings parsing.
- Individual block renderers.

Integration tests:

- Prisma schema constraints.
- Route lookup and redirect behavior.
- Worker processing for page metadata and snapshots with mocked Notion API responses.
- Public/private transition behavior.

End-to-end checks:

- A public Notion page snapshot renders through the web app.
- A slug alias redirects directly to the current canonical route.
- A page with missing `public_url` is not served.
- Custom CSS and global settings are applied.

## Open Extension Points

These are intentional extension points, not MVP tasks:

- Page-level settings overrides.
- Advanced refresh policy.
- Admin UI.
- Manual refresh endpoint.
- Multi-site support.
- Additional graph edge discovery.
- More complete Notion block coverage.
- Import from public Notion URLs.

## Approval State

The design reflects the following approved choices:

- Use the official Notion API, not public page scraping.
- Use Notion public share as access control for normal pages.
- Keep settings private and integration-accessible only.
- Discover pages from a root page graph.
- Use simple per-target refresh intervals with an extensible policy boundary.
- Generate routes from page titles.
- Preserve all previous slugs as direct redirects to the latest canonical slug.
- Limit the settings database MVP to global settings.
- Render pages as close to Notion as practical.
- Deploy to the user's k3s cluster.
- Use PostgreSQL, Next.js, TypeScript, Prisma, and a separate sync worker.
