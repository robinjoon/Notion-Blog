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
