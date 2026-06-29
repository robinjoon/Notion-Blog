Status: DONE

Commit SHA(s):
- 5ff003f

Files changed:
- /Users/imsubin/Documents/Notion-Blog/src/server/repositories.ts
- /Users/imsubin/Documents/Notion-Blog/src/server/settings-service.ts
- /Users/imsubin/Documents/Notion-Blog/src/server/page-service.ts
- /Users/imsubin/Documents/Notion-Blog/src/worker/sync-service.ts
- /Users/imsubin/Documents/Notion-Blog/tests/server/repositories.test.ts
- /Users/imsubin/Documents/Notion-Blog/tests/server/settings-service.test.ts
- /Users/imsubin/Documents/Notion-Blog/tests/worker/sync-service.test.ts

Verification commands run and exact outcomes:
- `CI=true pnpm test:run tests/server tests/worker` -> FAIL as expected before implementation; missing imports for `@/server/repositories`, `@/server/settings-service`, and `@/worker/sync-service`.
- `CI=true pnpm test:run tests/server tests/worker` -> FAIL once during implementation; `syncPage("page-a")` was forwarding normalized `pagea` instead of the requested `page-a`.
- `CI=true pnpm test:run tests/server tests/worker` -> PASS. `Test Files 4 passed (4)`, `Tests 11 passed (11)`.
- `CI=true pnpm typecheck` -> FAIL once during implementation; strict typing issues in `headJson`, `RefreshTargetRecord`, and `createSyncService` repository dependency requirements.
- `CI=true pnpm test:run tests/server tests/worker` -> PASS after type fixes. `Test Files 4 passed (4)`, `Tests 11 passed (11)`.
- `CI=true pnpm typecheck` -> PASS.

Self-review notes:
- Kept changes scoped to the Task 5 source and test files plus this report.
- `syncSettings()` queries the settings database through `NotionGateway.querySettingsDatabase()`, parses via `parseSettingsRows()`, persists `headJson`, upserts the root route as `/`, and schedules refresh targets through injected repository methods.
- `syncPage(pageId)` preserves the requested `pageId` for persistence calls, marks private pages without snapshot collection, and stores snapshots only for public pages.
- `resolveRoute(slug)` returns canonical pages directly and active alias redirects as the current canonical slug.
- `claimDueRefreshTargets()` uses `lockedAt` and `lockedBy` updates to claim unlocked due targets without requiring a live database in tests.

Concerns:
- None.

---

Re-review follow-up:

Status: DONE

Commit SHA(s):
- 587ea51

Files changed:
- /Users/imsubin/Documents/Notion-Blog/src/server/repositories.ts
- /Users/imsubin/Documents/Notion-Blog/src/server/page-service.ts
- /Users/imsubin/Documents/Notion-Blog/tests/server/repositories.test.ts
- /Users/imsubin/Documents/Notion-Blog/tests/worker/sync-service.test.ts

Verification commands run and exact outcomes:
- `CI=true pnpm test:run tests/server tests/worker` -> FAIL after adding the public-page reschedule assertions; public sync was not forwarding `syncedAt`/`nextRefreshAt`, and repository refresh-target updates still used immediate `now`.
- `CI=true pnpm test:run tests/server tests/worker` -> PASS. `Test Files 4 passed (4)`, `Tests 14 passed (14)`.
- `CI=true pnpm typecheck` -> PASS.

Self-review notes:
- Public `syncPage()` now computes `syncedAt` and `nextRefreshAt` using `simpleRefreshPolicy()` before snapshot persistence.
- `upsertPageSnapshot()` now accepts refresh-completion timing and writes `nextRefreshAt`, `lastSyncedAt`, cleared locks, and reset failure state on both refresh-target create and update.
- Added narrow regression coverage in the worker service and repository tests for public-page rescheduling.

Concerns:
- None.

---

Review follow-up:

Status: DONE

Commit SHA(s):
- 1316415

Files changed:
- /Users/imsubin/Documents/Notion-Blog/src/server/repositories.ts
- /Users/imsubin/Documents/Notion-Blog/src/server/settings-service.ts
- /Users/imsubin/Documents/Notion-Blog/src/server/page-service.ts
- /Users/imsubin/Documents/Notion-Blog/src/worker/sync-service.ts
- /Users/imsubin/Documents/Notion-Blog/tests/server/repositories.test.ts
- /Users/imsubin/Documents/Notion-Blog/tests/server/settings-service.test.ts
- /Users/imsubin/Documents/Notion-Blog/tests/worker/sync-service.test.ts

Verification commands run and exact outcomes:
- `CI=true pnpm test:run tests/server tests/worker` -> FAIL after adding review-regression tests; root placeholder/root move, normalized page ID persistence, and private-page refresh completion all exposed gaps.
- `CI=true pnpm test:run tests/server tests/worker` -> PASS. `Test Files 4 passed (4)`, `Tests 13 passed (13)`.
- `CI=true pnpm typecheck` -> PASS.

Self-review notes:
- Root route assignment now happens inside `upsertSettingsSnapshot()` in the repository transaction, including a minimal `NotionPage` placeholder upsert to satisfy the `PageRoute.pageId` foreign key.
- Existing `/` ownership is explicitly moved off the prior root before assigning `/` to the new root, and the new root's previous canonical slug is preserved as an active alias.
- Private-page sync now completes the page refresh target lifecycle by clearing lock state, stamping `lastSyncedAt`, resetting failures, and setting the next refresh time in the same repository transaction.
- `syncPage()` now persists the normalized page ID from `mapNotionPageMetadata()` instead of overwriting it with the caller-provided identifier.

Concerns:
- None.
