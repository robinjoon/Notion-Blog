import { collectPageSnapshot } from "@/notion/block-collector";
import { normalizeNotionPageId } from "@/domain/notion-link";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";
import type { SiteHeadSettings } from "@/domain/settings";
import type { NotionGateway } from "@/notion/gateway";
import { createNotionGateway } from "@/notion/gateway";
import { mapNotionPageMetadata } from "@/notion/page-mapper";
import { createBlogRepository, type BlogRepository, type CachedPageContent, type SiteSettingsSnapshot } from "@/server/repositories";

interface PageServiceDependencies {
  notion: NotionGateway;
  repository: Pick<BlogRepository, "markPagePrivate" | "upsertPageSnapshot"> &
    Partial<
      Pick<BlogRepository, "ensureRefreshTarget" | "getPageContent" | "getSiteSettings" | "hasPageRefreshTarget" | "resolveRoute">
    >;
  settingsDatabaseId?: string;
  now?: () => Date;
}

export type PageLookupResult =
  | { kind: "page"; slug: string; page: CachedPageContent; snapshot: CachedPageContent["snapshot"] }
  | { kind: "redirect"; destination: string }
  | { kind: "notFound" };

const unconfiguredNotionGateway: NotionGateway = {
  async retrievePage() {
    throw new Error("notion sync is not configured");
  },
  async retrieveBlockChildren() {
    throw new Error("notion sync is not configured");
  },
  async querySettingsDatabase() {
    throw new Error("notion sync is not configured");
  }
};

function normalizeSlug(slug: string): string {
  if (!slug) {
    return "/";
  }

  return slug.startsWith("/") ? slug : `/${slug}`;
}

async function loadResolvedPage(
  repository: Pick<BlogRepository, "getPageContent" | "resolveRoute">,
  slug: string
): Promise<PageLookupResult> {
  const route = await repository.resolveRoute(normalizeSlug(slug));

  if (route.kind === "redirect") {
    return {
      kind: "redirect",
      destination: route.destination
    };
  }

  if (route.kind === "not-found") {
    return { kind: "notFound" };
  }

  const page = await repository.getPageContent(route.pageId);
  if (!page) {
    return { kind: "notFound" };
  }

  return {
    kind: "page",
    slug: route.slug,
    page,
    snapshot: page.snapshot
  };
}

export function createPageService({
  notion,
  repository,
  settingsDatabaseId,
  now = () => new Date()
}: PageServiceDependencies) {
  const canReadRoutes = (
    value: PageServiceDependencies["repository"]
  ): value is Pick<BlogRepository, "getPageContent" | "resolveRoute"> &
    Pick<PageServiceDependencies["repository"], "markPagePrivate" | "upsertPageSnapshot"> =>
    typeof value.getPageContent === "function" && typeof value.resolveRoute === "function";

  return {
    async syncPage(pageId: string): Promise<void> {
      const page = await notion.retrievePage(pageId);
      const metadata = mapNotionPageMetadata(page as Parameters<typeof mapNotionPageMetadata>[0]);

      if (metadata.publicUrl === null) {
        const syncedAt = now();
        await repository.markPagePrivate({
          pageId: metadata.pageId,
          syncedAt,
          nextRefreshAt: simpleRefreshPolicy(
            {
              targetKind: "page",
              targetId: metadata.pageId,
              failureCount: 0,
              lastSyncedAt: syncedAt
            },
            syncedAt
          )
        });
        return;
      }

      const syncedAt = now();
      const snapshot = await collectPageSnapshot(notion, pageId, metadata);
      await repository.upsertPageSnapshot({
        ...snapshot,
        syncedAt,
        nextRefreshAt: simpleRefreshPolicy(
          {
            targetKind: "page",
            targetId: metadata.pageId,
            failureCount: 0,
            lastSyncedAt: syncedAt
          },
          syncedAt
        )
      });
    },

    async getRootPage(): Promise<PageLookupResult> {
      if (!canReadRoutes(repository)) {
        throw new Error("route reads are not configured");
      }

      return loadResolvedPage(repository, "/");
    },

    async getPageBySlug(slug: string): Promise<PageLookupResult> {
      if (!canReadRoutes(repository)) {
        throw new Error("route reads are not configured");
      }

      return loadResolvedPage(repository, slug);
    },

    async collectLinkedPage(pageId: string): Promise<{ kind: "redirect"; destination: string } | { kind: "notFound" }> {
      const normalizedPageId = normalizeNotionPageId(pageId);
      if (!normalizedPageId) {
        return { kind: "notFound" };
      }

      if (typeof repository.getPageContent !== "function") {
        throw new Error("page reads are not configured");
      }

      const page = await repository.getPageContent(normalizedPageId);
      if (page) {
        return {
          kind: "redirect",
          destination: page.slug
        };
      }

      if (typeof repository.hasPageRefreshTarget !== "function" || !(await repository.hasPageRefreshTarget(normalizedPageId))) {
        return { kind: "notFound" };
      }

      if (typeof repository.ensureRefreshTarget === "function") {
        await repository.ensureRefreshTarget({
          targetKind: "page",
          targetId: normalizedPageId,
          nextRefreshAt: now()
        });
      }

      return { kind: "notFound" };
    },

    async getSiteSettings(): Promise<SiteSettingsSnapshot | null> {
      if (typeof repository.getSiteSettings !== "function") {
        return null;
      }

      if (!settingsDatabaseId) {
        return null;
      }

      return repository.getSiteSettings(settingsDatabaseId);
    },

    async getSiteHeadSettings(): Promise<SiteHeadSettings> {
      return (await this.getSiteSettings())?.head ?? {}
    }
  };
}

export async function getPageService() {
  const { prisma } = await import("@/server/db");

  return createPageService({
    notion: unconfiguredNotionGateway,
    repository: createBlogRepository(prisma),
    settingsDatabaseId: process.env.SETTINGS_DATABASE_ID
  });
}

export async function getPageCollectionService() {
  const notionToken = process.env.NOTION_TOKEN;
  if (!notionToken) {
    throw new Error("NOTION_TOKEN is required");
  }

  const { prisma } = await import("@/server/db");

  return createPageService({
    notion: createNotionGateway(notionToken),
    repository: createBlogRepository(prisma),
    settingsDatabaseId: process.env.SETTINGS_DATABASE_ID
  });
}
