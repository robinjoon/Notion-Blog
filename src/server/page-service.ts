import { collectPageSnapshot } from "@/notion/block-collector";
import type { NotionGateway } from "@/notion/gateway";
import { mapNotionPageMetadata } from "@/notion/page-mapper";
import type { BlogRepository } from "@/server/repositories";

interface PageServiceDependencies {
  notion: NotionGateway;
  repository: Pick<BlogRepository, "markPagePrivate" | "upsertPageSnapshot">;
}

export function createPageService({ notion, repository }: PageServiceDependencies) {
  return {
    async syncPage(pageId: string): Promise<void> {
      const page = await notion.retrievePage(pageId);
      const metadata = {
        ...mapNotionPageMetadata(page as Parameters<typeof mapNotionPageMetadata>[0]),
        pageId
      };

      if (metadata.publicUrl === null) {
        await repository.markPagePrivate(metadata.pageId);
        return;
      }

      const snapshot = await collectPageSnapshot(notion, pageId, metadata);
      await repository.upsertPageSnapshot(snapshot);
    }
  };
}
