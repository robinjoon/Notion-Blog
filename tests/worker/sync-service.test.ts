import { describe, expect, it, vi } from "vitest";
import { createSyncService } from "@/worker/sync-service";

describe("sync service", () => {
  it("ensures the first settings refresh target exists without overwriting an existing row", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const repository = {
      upsertPageSnapshot: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined),
      ensureRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };
    const gateway = {
      retrievePage: vi.fn(),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };

    const service = createSyncService({
      repository,
      notion: gateway,
      settingsDatabaseId: "settings-db",
      now: () => now
    });

    await service.ensureSettingsRefreshTarget();

    expect(repository.ensureRefreshTarget).toHaveBeenCalledWith({
      targetKind: "settings",
      targetId: "settings-db",
      nextRefreshAt: now
    });
  });

  it("stores a snapshot for the normalized notion page id and reschedules refresh", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const repository = {
      upsertPageSnapshot: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined)
    };
    const gateway = {
      retrievePage: vi.fn().mockResolvedValue({
        id: "01234567-89ab-cdef-0123-456789abcdef",
        url: "https://www.notion.so/page-a",
        public_url: "https://site.notion.site/page-a",
        last_edited_time: "2026-06-30T00:00:00.000Z",
        properties: {
          title: {
            type: "title",
            title: [{ plain_text: "Page A" }]
          }
        }
      }),
      retrieveBlockChildren: vi.fn().mockResolvedValue([]),
      querySettingsDatabase: vi.fn()
    };

    const service = createSyncService({ repository, notion: gateway, now: () => now });

    await service.syncPage("01234567-89ab-cdef-0123-456789abcdef");

    expect(repository.upsertPageSnapshot).toHaveBeenCalledWith(
      expect.objectContaining({
        pageId: "0123456789abcdef0123456789abcdef",
        syncedAt: now,
        nextRefreshAt: new Date("2026-06-30T00:15:00.000Z")
      })
    );
    expect(repository.markPagePrivate).not.toHaveBeenCalled();
  });

  it("marks private pages and completes the refresh lifecycle", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const repository = {
      upsertPageSnapshot: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined)
    };
    const gateway = {
      retrievePage: vi.fn().mockResolvedValue({
        id: "fedcba98-7654-3210-fedc-ba9876543210",
        url: "https://www.notion.so/page-a",
        public_url: null,
        last_edited_time: "2026-06-30T00:00:00.000Z",
        properties: {
          title: {
            type: "title",
            title: [{ plain_text: "Page A" }]
          }
        }
      }),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };

    const service = createSyncService({ repository, notion: gateway, now: () => now });

    await service.syncPage("fedcba98-7654-3210-fedc-ba9876543210");

    expect(repository.markPagePrivate).toHaveBeenCalledWith({
      pageId: "fedcba9876543210fedcba9876543210",
      syncedAt: now,
      nextRefreshAt: new Date("2026-06-30T00:15:00.000Z")
    });
    expect(gateway.retrieveBlockChildren).not.toHaveBeenCalled();
    expect(repository.upsertPageSnapshot).not.toHaveBeenCalled();
  });
});
