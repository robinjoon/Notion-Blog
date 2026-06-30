import { describe, expect, it, vi } from "vitest";
import type { SettingsRow } from "@/domain/settings";
import { createSettingsService } from "@/server/settings-service";

describe("settings service", () => {
  it("syncs one settings snapshot and refresh state from the settings database", async () => {
    const rows: SettingsRow[] = [
      { key: "rootPage", kind: "page", enabled: true, page: "https://www.notion.so/Root-0123456789abcdef0123456789abcdef", data: "" },
      { key: "header", kind: "blocks", enabled: true, page: "11111111111111111111111111111111", data: "" },
      { key: "footer", kind: "blocks", enabled: true, page: "22222222222222222222222222222222", data: "" },
      { key: "head", kind: "head", enabled: true, page: "", data: "{\"siteName\":\"Notion Blog\",\"defaultTitle\":\"Blog\"}" }
    ];
    const gateway = {
      querySettingsDatabase: vi.fn().mockResolvedValue(rows),
      retrievePage: vi.fn(),
      retrieveBlockChildren: vi.fn()
    };
    const repository = {
      upsertSettingsSnapshot: vi.fn().mockResolvedValue(undefined),
      upsertRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };

    const service = createSettingsService({
      notion: gateway,
      repository,
      settingsDatabaseId: "settings-db"
    });

    await expect(service.syncSettings()).resolves.toEqual({
      rootPageId: "0123456789abcdef0123456789abcdef",
      headerPageId: "11111111111111111111111111111111",
      footerPageId: "22222222222222222222222222222222",
      head: {
        siteName: "Notion Blog",
        defaultTitle: "Blog"
      }
    });

    expect(repository.upsertSettingsSnapshot).toHaveBeenCalledWith({
      settingsDatabaseId: "settings-db",
      rootPageId: "0123456789abcdef0123456789abcdef",
      headerPageId: "11111111111111111111111111111111",
      footerPageId: "22222222222222222222222222222222",
      headJson: {
        siteName: "Notion Blog",
        defaultTitle: "Blog"
      }
    });
  });

  it("maps raw Notion settings database rows before parsing", async () => {
    const gateway = {
      querySettingsDatabase: vi.fn().mockResolvedValue([
        {
          properties: {
            Key: {
              type: "title",
              title: [{ plain_text: "rootPage" }]
            },
            Kind: {
              type: "select",
              select: { name: "page" }
            },
            Enabled: {
              type: "checkbox",
              checkbox: true
            },
            Page: {
              type: "url",
              url: "https://workspace.notion.site/Root-0123456789abcdef0123456789abcdef"
            },
            Data: {
              type: "rich_text",
              rich_text: []
            }
          }
        },
        {
          properties: {
            Key: {
              type: "title",
              title: [{ plain_text: "header" }]
            },
            Kind: {
              type: "select",
              select: { name: "blocks" }
            },
            Enabled: {
              type: "checkbox",
              checkbox: true
            },
            Page: {
              type: "rich_text",
              rich_text: [{ plain_text: "11111111111111111111111111111111" }]
            },
            Data: {
              type: "rich_text",
              rich_text: []
            }
          }
        },
        {
          properties: {
            Key: {
              type: "title",
              title: [{ plain_text: "footer" }]
            },
            Kind: {
              type: "select",
              select: { name: "blocks" }
            },
            Enabled: {
              type: "checkbox",
              checkbox: true
            },
            Page: {
              type: "url",
              url: "https://workspace.notion.site/Footer-22222222222222222222222222222222"
            },
            Data: {
              type: "rich_text",
              rich_text: []
            }
          }
        },
        {
          properties: {
            Key: {
              type: "title",
              title: [{ plain_text: "head" }]
            },
            Kind: {
              type: "select",
              select: { name: "head" }
            },
            Enabled: {
              type: "checkbox",
              checkbox: true
            },
            Page: {
              type: "rich_text",
              rich_text: []
            },
            Data: {
              type: "rich_text",
              rich_text: [{ plain_text: "{\"siteName\":\"Notion Blog\",\"defaultTitle\":\"Blog\"}" }]
            }
          }
        }
      ]),
      retrievePage: vi.fn(),
      retrieveBlockChildren: vi.fn()
    };
    const repository = {
      upsertSettingsSnapshot: vi.fn().mockResolvedValue(undefined),
      upsertRefreshTarget: vi.fn().mockResolvedValue(undefined)
    };

    const service = createSettingsService({
      notion: gateway,
      repository,
      settingsDatabaseId: "settings-db"
    });

    await expect(service.syncSettings()).resolves.toEqual({
      rootPageId: "0123456789abcdef0123456789abcdef",
      headerPageId: "11111111111111111111111111111111",
      footerPageId: "22222222222222222222222222222222",
      head: {
        siteName: "Notion Blog",
        defaultTitle: "Blog"
      }
    });
  });
});
