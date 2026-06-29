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
              pageId: "0123456789abcdef0123456789abcdef",
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
          pageId: "0123456789abcdef0123456789abcdef",
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
      pageId: "0123456789abcdef0123456789abcdef",
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
          targetId: "settings-db"
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

  it("claims stale locked refresh targets but leaves fresh locks alone", async () => {
    const now = new Date("2026-06-30T00:10:00.000Z");
    const staleLock = new Date("2026-06-30T00:03:59.000Z");
    const freshLock = new Date("2026-06-30T00:09:00.000Z");
    const updateMany = vi
      .fn()
      .mockResolvedValueOnce({ count: 1 })
      .mockResolvedValueOnce({ count: 0 });
    const findMany = vi
      .fn()
      .mockResolvedValueOnce([
        {
          targetKind: "SETTINGS",
          targetId: "stale-settings",
          nextRefreshAt: new Date("2026-06-30T00:00:00.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: staleLock,
          lockedBy: "worker-dead",
          createdAt: now,
          updatedAt: now
        },
        {
          targetKind: "PAGE",
          targetId: "fresh-page",
          nextRefreshAt: new Date("2026-06-30T00:01:00.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: freshLock,
          lockedBy: "worker-live",
          createdAt: now,
          updatedAt: now
        }
      ])
      .mockResolvedValueOnce([
        {
          targetKind: "SETTINGS",
          targetId: "stale-settings",
          nextRefreshAt: new Date("2026-06-30T00:00:00.000Z"),
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

    expect(updateMany).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        where: expect.objectContaining({
          targetKind: "SETTINGS",
          targetId: "stale-settings"
        }),
        data: {
          lockedAt: now,
          lockedBy: "worker-a"
        }
      })
    );
    expect(updateMany).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        where: expect.objectContaining({
          targetKind: "PAGE",
          targetId: "fresh-page"
        })
      })
    );
    expect(claimed).toEqual([
      expect.objectContaining({
        targetKind: "settings",
        targetId: "stale-settings",
        lockedAt: now,
        lockedBy: "worker-a"
      })
    ]);
  });

  it("creates a refresh target only when it does not already exist", async () => {
    const upsert = vi.fn().mockResolvedValue(undefined);
    const repository = createBlogRepository({
      refreshTarget: { upsert }
    } as never);

    await repository.ensureRefreshTarget({
      targetKind: "settings",
      targetId: "settings-db",
      nextRefreshAt: new Date("2026-06-30T00:00:00.000Z")
    });

    expect(upsert).toHaveBeenCalledWith({
      where: {
        targetKind_targetId: {
          targetKind: "SETTINGS",
          targetId: "settings-db"
        }
      },
      update: {},
      create: {
        targetKind: "SETTINGS",
        targetId: "settings-db",
        nextRefreshAt: new Date("2026-06-30T00:00:00.000Z"),
        lastSyncedAt: null,
        failureCount: 0,
        lastError: null,
        lockedAt: null,
        lockedBy: null
      }
    });
  });

  it("creates a root-page placeholder before assigning the first root route", async () => {
    const notionPageUpsert = vi.fn().mockResolvedValue(undefined);
    const siteSettingsUpsert = vi.fn().mockResolvedValue(undefined);
    const pageRouteFindUnique = vi.fn().mockResolvedValue(null);
    const pageRouteUpdateMany = vi.fn().mockResolvedValue({ count: 0 });
    const pageRouteUpsert = vi.fn().mockResolvedValue(undefined);
    const tx = {
      notionPage: { upsert: notionPageUpsert },
      pageRoute: {
        findUnique: pageRouteFindUnique,
        updateMany: pageRouteUpdateMany,
        upsert: pageRouteUpsert
      },
      slugAlias: {},
      pageSnapshot: { upsert: vi.fn() },
      refreshTarget: { upsert: vi.fn() },
      siteSettings: { upsert: siteSettingsUpsert }
    };
    const prisma = {
      ...tx,
      $transaction: vi.fn(async (work: (inner: typeof tx) => Promise<void>) => work(tx))
    };

    const repository = createBlogRepository(prisma as never);

    await repository.upsertSettingsSnapshot({
      settingsDatabaseId: "settings-db",
      rootPageId: "0123456789abcdef0123456789abcdef",
      headJson: {}
    });

    expect(notionPageUpsert).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { pageId: "0123456789abcdef0123456789abcdef" }
      })
    );
    expect(pageRouteUpsert).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { pageId: "0123456789abcdef0123456789abcdef" },
        create: expect.objectContaining({
          pageId: "0123456789abcdef0123456789abcdef",
          canonicalSlug: "/"
        })
      })
    );
    expect(pageRouteUpdateMany).not.toHaveBeenCalled();
  });

  it("moves the root route from the old root page to the new root page atomically", async () => {
    const notionPageUpsert = vi.fn().mockResolvedValue(undefined);
    const siteSettingsUpsert = vi.fn().mockResolvedValue(undefined);
    const pageRouteFindUnique = vi
      .fn()
      .mockResolvedValueOnce({ pageId: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", canonicalSlug: "/" })
      .mockResolvedValueOnce({ pageId: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", canonicalSlug: "/new-root" });
    const pageRouteUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
    const pageRouteUpsert = vi.fn().mockResolvedValue(undefined);
    const tx = {
      notionPage: { upsert: notionPageUpsert },
      pageRoute: {
        findUnique: pageRouteFindUnique,
        updateMany: pageRouteUpdateMany,
        upsert: pageRouteUpsert
      },
      slugAlias: {
        upsert: vi.fn().mockResolvedValue(undefined)
      },
      pageSnapshot: { upsert: vi.fn() },
      refreshTarget: { upsert: vi.fn() },
      siteSettings: {
        upsert: siteSettingsUpsert
      }
    };
    const prisma = {
      ...tx,
      $transaction: vi.fn(async (work: (inner: typeof tx) => Promise<void>) => work(tx))
    };

    const repository = createBlogRepository(prisma as never);

    await repository.upsertSettingsSnapshot({
      settingsDatabaseId: "settings-db",
      rootPageId: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      headJson: {}
    });

    expect(pageRouteUpdateMany).toHaveBeenCalledWith({
      where: {
        pageId: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        canonicalSlug: "/"
      },
      data: {
        canonicalSlug: "/page-aaaaaaaa"
      }
    });
    expect(pageRouteUpsert).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { pageId: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" },
        update: expect.objectContaining({
          canonicalSlug: "/"
        })
      })
    );
  });

  it("reschedules the public page refresh target after snapshot persistence", async () => {
    const refreshTargetUpsert = vi.fn().mockResolvedValue(undefined);
    const tx = {
      notionPage: {
        upsert: vi.fn().mockResolvedValue(undefined)
      },
      pageRoute: {
        findUnique: vi.fn().mockResolvedValue(null),
        upsert: vi.fn().mockResolvedValue(undefined)
      },
      slugAlias: {
        findMany: vi.fn().mockResolvedValue([]),
        findUnique: vi.fn().mockResolvedValue(null)
      },
      pageSnapshot: {
        upsert: vi.fn().mockResolvedValue(undefined)
      },
      refreshTarget: {
        upsert: refreshTargetUpsert
      },
      siteSettings: {
        upsert: vi.fn().mockResolvedValue(undefined)
      }
    };
    const prisma = {
      ...tx,
      $transaction: vi.fn(async (work: (inner: typeof tx) => Promise<void>) => work(tx))
    };
    const repository = createBlogRepository(prisma as never);
    const syncedAt = new Date("2026-06-30T00:00:00.000Z");
    const nextRefreshAt = new Date("2026-06-30T00:15:00.000Z");

    await repository.upsertPageSnapshot({
      pageId: "0123456789abcdef0123456789abcdef",
      title: "Page A",
      notionUrl: "https://www.notion.so/page-a",
      publicUrl: "https://site.notion.site/page-a",
      lastEditedTime: "2026-06-30T00:00:00.000Z",
      blocks: [],
      syncedAt,
      nextRefreshAt
    });

    expect(refreshTargetUpsert).toHaveBeenCalledWith(
      expect.objectContaining({
        update: expect.objectContaining({
          nextRefreshAt,
          lastSyncedAt: syncedAt,
          failureCount: 0,
          lastError: null,
          lockedAt: null,
          lockedBy: null
        }),
        create: expect.objectContaining({
          nextRefreshAt,
          lastSyncedAt: syncedAt,
          failureCount: 0
        })
      })
    );
  });

  it("completes a claimed refresh target by clearing the lock and scheduling the next run", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const prisma = {
      refreshTarget: {
        updateMany
      }
    };

    const repository = createBlogRepository(prisma as never);

    await repository.completeRefreshTarget(
      {
        targetKind: "page",
        targetId: "page-a",
        nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
        lastSyncedAt: null,
        failureCount: 2,
        lastError: "previous failure",
        lockedAt: now,
        lockedBy: "worker-a"
      },
      now
    );

    expect(updateMany).toHaveBeenCalledWith({
      where: {
        targetKind: "PAGE",
        targetId: "page-a",
        lockedAt: now,
        lockedBy: "worker-a"
      },
      data: {
        nextRefreshAt: new Date("2026-06-30T00:15:00.000Z"),
        lastSyncedAt: now,
        failureCount: 0,
        lastError: null,
        lockedAt: null,
        lockedBy: null
      }
    });
  });

  it("fails a claimed refresh target by incrementing failures, storing the error, and clearing the lock", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const prisma = {
      refreshTarget: {
        updateMany
      }
    };

    const repository = createBlogRepository(prisma as never);

    await repository.failRefreshTarget(
      {
        targetKind: "settings",
        targetId: "settings-db",
        nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
        lastSyncedAt: new Date("2026-06-29T23:00:00.000Z"),
        failureCount: 1,
        lastError: null,
        lockedAt: now,
        lockedBy: "worker-a"
      },
      new Error("notion timeout"),
      now
    );

    expect(updateMany).toHaveBeenCalledWith({
      where: {
        targetKind: "SETTINGS",
        targetId: "settings-db",
        lockedAt: now,
        lockedBy: "worker-a"
      },
      data: {
        nextRefreshAt: new Date("2026-06-30T00:06:00.000Z"),
        lastSyncedAt: new Date("2026-06-29T23:00:00.000Z"),
        failureCount: 2,
        lastError: "notion timeout",
        lockedAt: null,
        lockedBy: null
      }
    });
  });
});
