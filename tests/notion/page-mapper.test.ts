import { describe, expect, it } from "vitest";
import { mapNotionPageMetadata } from "@/notion/page-mapper";

describe("Notion page mapper", () => {
  it("maps public_url and title from a Notion page response", () => {
    const metadata = mapNotionPageMetadata({
      id: "01234567-89ab-cdef-0123-456789abcdef",
      url: "https://www.notion.so/Test-0123456789abcdef0123456789abcdef",
      public_url: "https://site.notion.site/Test-0123456789abcdef0123456789abcdef",
      last_edited_time: "2026-06-29T00:00:00.000Z",
      properties: {
        title: {
          type: "title",
          title: [{ plain_text: "Hello Notion" }]
        }
      }
    });

    expect(metadata).toEqual({
      pageId: "0123456789abcdef0123456789abcdef",
      title: "Hello Notion",
      notionUrl: "https://www.notion.so/Test-0123456789abcdef0123456789abcdef",
      publicUrl: "https://site.notion.site/Test-0123456789abcdef0123456789abcdef",
      lastEditedTime: "2026-06-29T00:00:00.000Z"
    });
  });
});
