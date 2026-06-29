import { describe, expect, it, vi } from "vitest";
import { createBlogRepository, type RefreshTargetRecord } from "@/server/repositories";

describe("blog repository", () => {
  it("resolves an active alias to the current canonical slug", async () => {
    const prisma = {
      pageRoute: {
        findUnique: vi.fn().mockResolvedValue(null)
      },
      slugAlias: {
        findUnique: vi.fn().mockResolvedValue({
          slug: "/old-slug",
          status: "ACTIVE",
          page: {
            route: {
              pageId: "page-a",
              canonicalSlug: "/current-slug",
              isActive: true
            }
          }
        })
      }
    };

    const repository = createBlogRepository(prisma as never);

    await expect(repository.resolveRoute("/old-slug")).resolves.toEqual({
      kind: "redirect",
      destination: "/current-slug"
    });
  });

  it("resolves the root route to the configured root page", async () => {
    const prisma = {
      pageRoute: {
        findUnique: vi.fn().mockResolvedValue({
          pageId: "root-page",
          canonicalSlug: "/",
          isActive: true
        })
      },
      slugAlias: {
        findUnique: vi.fn().mockResolvedValue(null)
      }
    };

    const repository = createBlogRepository(prisma as never);

    await expect(repository.resolveRoute("/")).resolves.toEqual({
      kind: "page",
      pageId: "root-page",
      slug: "/"
    });
  });

  it("claims only due unlocked refresh targets and stamps the worker lock", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const findMany = vi
      .fn()
      .mockResolvedValueOnce([
        {
          targetKind: "SETTINGS",
          targetId: "settings-db",
          nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: null,
          lockedBy: null,
          createdAt: now,
          updatedAt: now
        }
      ])
      .mockResolvedValueOnce([
        {
          targetKind: "SETTINGS",
          targetId: "settings-db",
          nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: now,
          lockedBy: "worker-a",
          createdAt: now,
          updatedAt: now
        }
      ] satisfies RefreshTargetRecord[]);

    const prisma = {
      refreshTarget: {
        findMany,
        updateMany
      }
    };

    const repository = createBlogRepository(prisma as never);
    const claimed = await repository.claimDueRefreshTargets(now, "worker-a", 10);

    expect(updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          targetKind: "SETTINGS",
          targetId: "settings-db",
          lockedAt: null
        }),
        data: {
          lockedAt: now,
          lockedBy: "worker-a"
        }
      })
    );

    expect(claimed).toEqual([
      {
        targetKind: "settings",
        targetId: "settings-db",
        nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
        lastSyncedAt: null,
        failureCount: 0,
        lastError: null,
        lockedAt: now,
        lockedBy: "worker-a"
      }
    ]);
  });
});
