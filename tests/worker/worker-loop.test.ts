import { describe, expect, it, vi } from "vitest";
import { runWorkerOnce } from "@/worker/index";

describe("worker loop", () => {
  it("syncs claimed settings and page targets and completes each success", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const repository = {
      claimDueRefreshTargets: vi.fn().mockResolvedValue([
        {
          targetKind: "settings",
          targetId: "settings-db",
          nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: now,
          lockedBy: "worker-test"
        },
        {
          targetKind: "page",
          targetId: "page-a",
          nextRefreshAt: new Date("2026-06-29T23:59:30.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: now,
          lockedBy: "worker-test"
        }
      ]),
      completeRefreshTarget: vi.fn().mockResolvedValue(undefined),
      failRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };
    const syncSettings = vi.fn().mockResolvedValue(undefined);
    const syncPage = vi.fn().mockResolvedValue(undefined);

    const processed = await runWorkerOnce({
      repository,
      syncSettings,
      syncPage,
      workerId: "worker-test",
      now,
      limit: 10
    });

    expect(processed).toBe(2);
    expect(repository.claimDueRefreshTargets).toHaveBeenCalledWith(now, "worker-test", 10);
    expect(syncSettings).toHaveBeenCalledOnce();
    expect(syncPage).toHaveBeenCalledWith("page-a");
    expect(repository.completeRefreshTarget).toHaveBeenCalledTimes(2);
    expect(repository.completeRefreshTarget).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        targetKind: "settings",
        targetId: "settings-db",
        failureCount: 0,
        lastSyncedAt: null
      }),
      now
    );
    expect(repository.completeRefreshTarget).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        targetKind: "page",
        targetId: "page-a",
        failureCount: 0,
        lastSyncedAt: null
      }),
      now
    );
    expect(repository.failRefreshTarget).not.toHaveBeenCalled();
  });

  it("records a failed target and continues processing later claims", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const repository = {
      claimDueRefreshTargets: vi.fn().mockResolvedValue([
        {
          targetKind: "page",
          targetId: "page-a",
          nextRefreshAt: new Date("2026-06-29T23:59:00.000Z"),
          lastSyncedAt: null,
          failureCount: 1,
          lastError: null,
          lockedAt: now,
          lockedBy: "worker-test"
        },
        {
          targetKind: "settings",
          targetId: "settings-db",
          nextRefreshAt: new Date("2026-06-29T23:59:30.000Z"),
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: now,
          lockedBy: "worker-test"
        }
      ]),
      completeRefreshTarget: vi.fn().mockResolvedValue(undefined),
      failRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };
    const failure = new Error("notion timeout");
    const syncPage = vi.fn().mockRejectedValue(failure);
    const syncSettings = vi.fn().mockResolvedValue(undefined);

    const processed = await runWorkerOnce({
      repository,
      syncSettings,
      syncPage,
      workerId: "worker-test",
      now,
      limit: 10
    });

    expect(processed).toBe(2);
    expect(repository.failRefreshTarget).toHaveBeenCalledWith(
      expect.objectContaining({
        targetKind: "page",
        targetId: "page-a",
        failureCount: 1,
        lastSyncedAt: null
      }),
      failure,
      now
    );
    expect(syncSettings).toHaveBeenCalledOnce();
    expect(repository.completeRefreshTarget).toHaveBeenCalledWith(
      expect.objectContaining({
        targetKind: "settings",
        targetId: "settings-db",
        failureCount: 0,
        lastSyncedAt: null
      }),
      now
    );
  });
});
