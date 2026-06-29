import { Client } from "@notionhq/client";

export interface NotionGateway {
  retrievePage(pageId: string): Promise<unknown>;
  retrieveBlockChildren(blockId: string, startCursor?: string): Promise<unknown[]>;
  querySettingsDatabase(databaseId: string): Promise<unknown[]>;
}

export function createNotionGateway(token: string): NotionGateway {
  const client = new Client({ auth: token });

  return {
    async retrievePage(pageId) {
      return client.pages.retrieve({ page_id: pageId });
    },
    async retrieveBlockChildren(blockId, startCursor) {
      const results: unknown[] = [];
      let cursor: string | undefined = startCursor;

      do {
        const response = await client.blocks.children.list({
          block_id: blockId,
          start_cursor: cursor
        });

        results.push(...response.results);
        cursor = response.has_more ? response.next_cursor ?? undefined : undefined;
      } while (cursor);

      return results;
    },
    async querySettingsDatabase(databaseId) {
      const results: unknown[] = [];
      let cursor: string | undefined;

      do {
        const response = await client.dataSources.query({
          data_source_id: databaseId,
          start_cursor: cursor
        });

        results.push(...response.results);
        cursor = response.has_more ? response.next_cursor ?? undefined : undefined;
      } while (cursor);

      return results;
    }
  };
}
