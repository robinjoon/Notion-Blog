import { describe, expect, it } from "vitest";
import { collectPageSnapshot, type NotionGateway } from "@/notion/block-collector";

describe("block collector", () => {
  it("recursively collects child blocks", async () => {
    const gateway: NotionGateway = {
      retrievePage: async () => {
        throw new Error("not used");
      },
      retrieveBlockChildren: async (blockId: string) => {
        if (blockId === "page-a") {
          return [{ id: "paragraph-a", type: "paragraph", has_children: true, paragraph: { rich_text: [{ plain_text: "Parent" }] } }];
        }
        if (blockId === "paragraph-a") {
          return [{ id: "paragraph-b", type: "paragraph", has_children: false, paragraph: { rich_text: [{ plain_text: "Child" }] } }];
        }
        return [];
      },
      querySettingsDatabase: async () => []
    };

    const snapshot = await collectPageSnapshot(gateway, "page-a", {
      pageId: "page-a",
      title: "Page A",
      notionUrl: "https://www.notion.so/page-a",
      publicUrl: "https://site.notion.site/page-a",
      lastEditedTime: "2026-06-29T00:00:00.000Z"
    });

    expect(snapshot.blocks[0]).toMatchObject({
      id: "paragraph-a",
      type: "paragraph",
      hasChildren: true,
      children: [{ id: "paragraph-b", type: "paragraph" }]
    });
  });

  it("keeps the normalized metadata page id in the snapshot", async () => {
    const gateway: NotionGateway = {
      retrievePage: async () => {
        throw new Error("not used");
      },
      retrieveBlockChildren: async () => [],
      querySettingsDatabase: async () => []
    };

    const snapshot = await collectPageSnapshot(gateway, "01234567-89ab-cdef-0123-456789abcdef", {
      pageId: "0123456789abcdef0123456789abcdef",
      title: "Page A",
      notionUrl: "https://www.notion.so/page-a",
      publicUrl: "https://site.notion.site/page-a",
      lastEditedTime: "2026-06-29T00:00:00.000Z"
    });

    expect(snapshot.pageId).toBe("0123456789abcdef0123456789abcdef");
  });
});
