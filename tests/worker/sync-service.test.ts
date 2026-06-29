import { describe, expect, it, vi } from "vitest";
import { createSyncService } from "@/worker/sync-service";

describe("sync service", () => {
  it("stores a snapshot for public pages", async () => {
    const repository = {
      upsertPageSnapshot: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined)
    };
    const gateway = {
      retrievePage: vi.fn().mockResolvedValue({
        id: "page-a",
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

    const service = createSyncService({ repository, notion: gateway });

    await service.syncPage("page-a");

    expect(repository.upsertPageSnapshot).toHaveBeenCalledWith(
      expect.objectContaining({ pageId: "page-a" })
    );
    expect(repository.markPagePrivate).not.toHaveBeenCalled();
  });

  it("marks private pages without collecting a snapshot", async () => {
    const repository = {
      upsertPageSnapshot: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined)
    };
    const gateway = {
      retrievePage: vi.fn().mockResolvedValue({
        id: "page-a",
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

    const service = createSyncService({ repository, notion: gateway });

    await service.syncPage("page-a");

    expect(repository.markPagePrivate).toHaveBeenCalledWith("page-a");
    expect(gateway.retrieveBlockChildren).not.toHaveBeenCalled();
    expect(repository.upsertPageSnapshot).not.toHaveBeenCalled();
  });
});
