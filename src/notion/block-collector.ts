import { normalizeNotionPageId, parseNotionPageReference } from "@/domain/notion-link";
import type { NotionGateway as GatewayNotionGateway } from "@/notion/gateway";
import type { PageMetadata } from "@/notion/page-mapper";

export type NotionGateway = GatewayNotionGateway;

export interface NotionBlockSnapshot {
  id: string;
  type: string;
  hasChildren: boolean;
  data: Record<string, unknown>;
  children: NotionBlockSnapshot[];
}

export interface PageSnapshot extends PageMetadata {
  blocks: NotionBlockSnapshot[];
  linkedPageIds?: string[];
}

type NotionBlockLike = {
  id: string;
  type: string;
  has_children?: boolean;
} & Record<string, unknown>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isNotionBlockLike(value: unknown): value is NotionBlockLike {
  return isRecord(value) && typeof value.id === "string" && typeof value.type === "string";
}

function addNotionReference(value: string, linkedPageIds: Set<string>) {
  const reference = parseNotionPageReference(value);
  if (reference) {
    linkedPageIds.add(reference.pageId);
  }
}

function collectLinkedPageIdsFromValue(value: unknown, linkedPageIds: Set<string>, key?: string) {
  if (typeof value === "string") {
    if (key === "href" || key === "url") {
      addNotionReference(value, linkedPageIds);
    }
    return;
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      collectLinkedPageIdsFromValue(item, linkedPageIds);
    }
    return;
  }

  if (!isRecord(value)) {
    return;
  }

  if (value.type === "child_page" && typeof value.id === "string") {
    const pageId = normalizeNotionPageId(value.id);
    if (pageId) {
      linkedPageIds.add(pageId);
    }
  }

  const mention = value.mention;
  if (isRecord(mention) && mention.type === "page" && isRecord(mention.page) && typeof mention.page.id === "string") {
    const pageId = normalizeNotionPageId(mention.page.id);
    if (pageId) {
      linkedPageIds.add(pageId);
    }
  }

  for (const [childKey, childValue] of Object.entries(value)) {
    collectLinkedPageIdsFromValue(childValue, linkedPageIds, childKey);
  }
}

function collectLinkedPageIds(blocks: NotionBlockSnapshot[], currentPageId: string): string[] {
  const linkedPageIds = new Set<string>();
  for (const block of blocks) {
    collectLinkedPageIdsFromValue(block.data, linkedPageIds);
  }
  linkedPageIds.delete(currentPageId);
  return Array.from(linkedPageIds);
}

async function collectChildBlocks(
  gateway: NotionGateway,
  blockId: string
): Promise<NotionBlockSnapshot[]> {
  const results = await gateway.retrieveBlockChildren(blockId);
  const blocks = results.filter(isNotionBlockLike);

  return Promise.all(
    blocks.map(async (block) => ({
      id: block.id,
      type: block.type,
      hasChildren: block.has_children ?? false,
      data: block,
      children: block.has_children ? await collectChildBlocks(gateway, block.id) : []
    }))
  );
}

export async function collectPageSnapshot(
  gateway: NotionGateway,
  pageId: string,
  metadata: PageMetadata
): Promise<PageSnapshot> {
  const blocks = await collectChildBlocks(gateway, pageId);

  return {
    ...metadata,
    blocks,
    linkedPageIds: collectLinkedPageIds(blocks, metadata.pageId)
  };
}
