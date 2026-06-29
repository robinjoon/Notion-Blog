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
  return {
    ...metadata,
    blocks: await collectChildBlocks(gateway, pageId)
  };
}
