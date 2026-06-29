import { describe, expect, it, vi } from "vitest";
import { createPageService } from "@/server/page-service";
import type { NotionBlockSnapshot, PageSnapshot } from "@/notion/block-collector";
import type { SiteHeadSettings } from "@/domain/settings";

function createSnapshot(pageId: string, title: string): PageSnapshot {
  return {
    pageId,
    title,
    notionUrl: `https://notion.so/${pageId}`,
    publicUrl: `https://notion.site/${pageId}`,
    lastEditedTime: "2026-06-30T00:00:00.000Z",
    blocks: [
      {
        id: `${pageId}-paragraph`,
        type: "paragraph",
        hasChildren: false,
        data: { rich_text: [{ plain_text: `${title} body` }] },
        children: []
      } satisfies NotionBlockSnapshot
    ]
  };
}

function createPageRecord(snapshot: PageSnapshot, slug: string) {
  return {
    pageId: snapshot.pageId,
    title: snapshot.title,
    notionUrl: snapshot.notionUrl,
    publicUrl: snapshot.publicUrl,
    lastEditedTime: new Date(snapshot.lastEditedTime),
    slug,
    snapshot
  };
}

describe("page service", () => {
  it("returns the cached root page from the root route", async () => {
    const snapshot = createSnapshot("root-page", "Home");
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "page", pageId: snapshot.pageId, slug: "/" }),
      getPageContent: vi.fn().mockResolvedValue(createPageRecord(snapshot, "/")),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn(),
      upsertPageSnapshot: vi.fn()
    };
    const service = createPageService({
      notion: {
        retrievePage: vi.fn(),
        retrieveBlockChildren: vi.fn(),
        querySettingsDatabase: vi.fn()
      },
      repository
    });

    await expect(service.getRootPage()).resolves.toEqual({
      kind: "page",
      slug: "/",
      page: createPageRecord(snapshot, "/"),
      snapshot
    });
  });

  it("redirects alias slugs to the current canonical slug", async () => {
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "redirect", destination: "/new-slug" }),
      getPageContent: vi.fn(),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn(),
      upsertPageSnapshot: vi.fn()
    };
    const service = createPageService({
      notion: {
        retrievePage: vi.fn(),
        retrieveBlockChildren: vi.fn(),
        querySettingsDatabase: vi.fn()
      },
      repository
    });

    await expect(service.getPageBySlug("old-slug")).resolves.toEqual({
      kind: "redirect",
      destination: "/new-slug"
    });
  });

  it("returns notFound for the root route when the route exists but cached content is missing", async () => {
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "page", pageId: "root-page", slug: "/" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn(),
      upsertPageSnapshot: vi.fn()
    };
    const service = createPageService({
      notion: {
        retrievePage: vi.fn(),
        retrieveBlockChildren: vi.fn(),
        querySettingsDatabase: vi.fn()
      },
      repository
    });

    await expect(service.getRootPage()).resolves.toEqual({
      kind: "notFound"
    });
  });

  it("returns notFound for a slug route when the route exists but cached content is missing", async () => {
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "page", pageId: "page-a", slug: "/hello" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn(),
      upsertPageSnapshot: vi.fn()
    };
    const service = createPageService({
      notion: {
        retrievePage: vi.fn(),
        retrieveBlockChildren: vi.fn(),
        querySettingsDatabase: vi.fn()
      },
      repository
    });

    await expect(service.getPageBySlug("hello")).resolves.toEqual({
      kind: "notFound"
    });
  });

  it("collects a linked page and redirects to its canonical slug", async () => {
    const snapshot = createSnapshot("page-a", "Hello");
    const storedPages = new Map<string, ReturnType<typeof createPageRecord>>([
      [snapshot.pageId, createPageRecord(snapshot, "/hello")]
    ]);

    const repository = {
      resolveRoute: vi
        .fn()
        .mockImplementation(async (slug: string) => (slug === "/hello"
          ? { kind: "page", pageId: snapshot.pageId, slug: "/hello" }
          : { kind: "not-found" })),
      getPageContent: vi.fn().mockImplementation(async (pageId: string) => storedPages.get(pageId) ?? null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn().mockImplementation(async ({ pageId }: { pageId: string }) => {
        storedPages.delete(pageId);
      }),
      upsertPageSnapshot: vi.fn().mockImplementation(async (input: PageSnapshot) => {
        storedPages.set(input.pageId, createPageRecord(input, "/hello"));
      })
    };
    const notion = {
      retrievePage: vi.fn().mockResolvedValue({
        id: snapshot.pageId,
        url: snapshot.notionUrl,
        public_url: snapshot.publicUrl,
        last_edited_time: snapshot.lastEditedTime,
        properties: {
          title: {
            type: "title",
            title: [{ plain_text: snapshot.title }]
          }
        }
      }),
      retrieveBlockChildren: vi.fn().mockResolvedValue([
        {
          id: snapshot.blocks[0].id,
          type: "paragraph",
          has_children: false,
          rich_text: [{ plain_text: "Hello body" }]
        }
      ]),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository });

    await expect(service.collectLinkedPage("page-a")).resolves.toEqual({
      kind: "redirect",
      destination: "/hello"
    });
  });

  it("returns notFound when a collected linked page is private", async () => {
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "not-found" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn().mockResolvedValue(undefined),
      upsertPageSnapshot: vi.fn()
    };
    const notion = {
      retrievePage: vi.fn().mockResolvedValue({
        id: "private-page",
        url: "https://notion.so/private-page",
        public_url: null,
        last_edited_time: "2026-06-30T00:00:00.000Z",
        properties: {
          title: {
            type: "title",
            title: [{ plain_text: "Private page" }]
          }
        }
      }),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository });

    await expect(service.collectLinkedPage("private-page")).resolves.toEqual({
      kind: "notFound"
    });
  });
});
