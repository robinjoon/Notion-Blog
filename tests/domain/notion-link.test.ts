import { describe, expect, it } from "vitest";
import { parseNotionPageReference } from "@/domain/notion-link";

describe("Notion link parser", () => {
  it("extracts page id from Notion URL", () => {
    expect(parseNotionPageReference("https://www.notion.so/My-Page-0123456789abcdef0123456789abcdef")).toEqual({
      pageId: "0123456789abcdef0123456789abcdef"
    });
  });

  it("extracts page id from notion.site URLs", () => {
    expect(parseNotionPageReference("https://workspace.notion.site/My-Page-0123456789abcdef0123456789abcdef")).toEqual({
      pageId: "0123456789abcdef0123456789abcdef"
    });
  });

  it("extracts page id from a raw id", () => {
    expect(parseNotionPageReference("0123456789abcdef0123456789abcdef")).toEqual({
      pageId: "0123456789abcdef0123456789abcdef"
    });
  });

  it("ignores non-Notion URLs", () => {
    expect(parseNotionPageReference("https://example.com/post")).toBeNull();
  });
});
