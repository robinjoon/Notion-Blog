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

  it("redirects a cached linked page without syncing through Notion", async () => {
    const snapshot = createSnapshot("0123456789abcdef0123456789abcdef", "Hello");
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
      retrievePage: vi.fn().mockRejectedValue(new Error("should not sync cached links inline")),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository });

    await expect(service.collectLinkedPage(snapshot.pageId)).resolves.toEqual({
      kind: "redirect",
      destination: "/hello"
    });
    expect(notion.retrievePage).not.toHaveBeenCalled();
  });

  it("does not sync malformed linked page ids", async () => {
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "not-found" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      markPagePrivate: vi.fn().mockResolvedValue(undefined),
      upsertPageSnapshot: vi.fn()
    };
    const notion = {
      retrievePage: vi.fn().mockRejectedValue(new Error("should not call Notion for malformed ids")),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository });

    await expect(service.collectLinkedPage("private-page")).resolves.toEqual({
      kind: "notFound"
    });
    expect(notion.retrievePage).not.toHaveBeenCalled();
  });

  it("queues allowed uncached linked pages instead of syncing them inline", async () => {
    const now = new Date("2026-06-30T00:00:00.000Z");
    const linkedPageId = "fedcba9876543210fedcba9876543210";
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "not-found" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      hasPageRefreshTarget: vi.fn().mockResolvedValue(true),
      ensureRefreshTarget: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined),
      upsertPageSnapshot: vi.fn()
    };
    const notion = {
      retrievePage: vi.fn().mockRejectedValue(new Error("should not sync queued links inline")),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository, now: () => now });

    await expect(service.collectLinkedPage(linkedPageId)).resolves.toEqual({
      kind: "notFound"
    });
    expect(repository.ensureRefreshTarget).toHaveBeenCalledWith({
      targetKind: "page",
      targetId: linkedPageId,
      nextRefreshAt: now
    });
    expect(notion.retrievePage).not.toHaveBeenCalled();
  });

  it("does not queue unknown linked page ids that were not discovered earlier", async () => {
    const linkedPageId = "fedcba9876543210fedcba9876543210";
    const repository = {
      resolveRoute: vi.fn().mockResolvedValue({ kind: "not-found" }),
      getPageContent: vi.fn().mockResolvedValue(null),
      getSiteSettings: vi.fn().mockResolvedValue({ head: {} satisfies SiteHeadSettings }),
      hasPageRefreshTarget: vi.fn().mockResolvedValue(false),
      ensureRefreshTarget: vi.fn().mockResolvedValue(undefined),
      markPagePrivate: vi.fn().mockResolvedValue(undefined),
      upsertPageSnapshot: vi.fn()
    };
    const notion = {
      retrievePage: vi.fn().mockRejectedValue(new Error("should not call Notion for undiscovered ids")),
      retrieveBlockChildren: vi.fn(),
      querySettingsDatabase: vi.fn()
    };
    const service = createPageService({ notion, repository });

    await expect(service.collectLinkedPage(linkedPageId)).resolves.toEqual({
      kind: "notFound"
    });
    expect(repository.ensureRefreshTarget).not.toHaveBeenCalled();
    expect(notion.retrievePage).not.toHaveBeenCalled();
  });
});
