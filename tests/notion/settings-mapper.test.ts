import { describe, expect, it } from "vitest";
import type { SettingsRow } from "@/domain/settings";
import { mapSettingsRows } from "@/notion/settings-mapper";

describe("settings mapper", () => {
  it("passes through already-normalized settings rows for tests and mocks", () => {
    const rows: SettingsRow[] = [
      { key: "rootPage", kind: "page", enabled: true, page: "0123456789abcdef0123456789abcdef", data: "" }
    ];

    expect(mapSettingsRows(rows)).toEqual(rows);
  });

  it("maps raw Notion settings rows into the parser shape", () => {
    expect(
      mapSettingsRows([
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
              rich_text: [{ plain_text: "{\"siteName\":\"Notion Blog\"}" }]
            }
          }
        }
      ])
    ).toEqual([
      {
        key: "rootPage",
        kind: "page",
        enabled: true,
        page: "https://workspace.notion.site/Root-0123456789abcdef0123456789abcdef",
        data: ""
      },
      {
        key: "head",
        kind: "head",
        enabled: true,
        page: "",
        data: "{\"siteName\":\"Notion Blog\"}"
      }
    ]);
  });
});
