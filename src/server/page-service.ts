import { collectPageSnapshot } from "@/notion/block-collector";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";
import type { NotionGateway } from "@/notion/gateway";
import { mapNotionPageMetadata } from "@/notion/page-mapper";
import type { BlogRepository } from "@/server/repositories";

interface PageServiceDependencies {
  notion: NotionGateway;
  repository: Pick<BlogRepository, "markPagePrivate" | "upsertPageSnapshot">;
  now?: () => Date;
}

export function createPageService({ notion, repository, now = () => new Date() }: PageServiceDependencies) {
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
    }
  };
}
