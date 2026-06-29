export interface PageMetadata {
  pageId: string;
  title: string;
  notionUrl: string;
  publicUrl: string | null;
  lastEditedTime: string;
}

type NotionTitleSegment = {
  plain_text?: string;
};

type NotionTitleProperty = {
  type?: string;
  title?: NotionTitleSegment[];
};

type NotionPageLike = {
  id: string;
  url: string;
  public_url?: string | null;
  last_edited_time: string;
  properties?: Record<string, NotionTitleProperty>;
};

function normalizePageId(pageId: string): string {
  return pageId.replace(/-/g, "").toLowerCase();
}

function deriveTitle(properties: NotionPageLike["properties"]): string {
  if (!properties) {
    return "";
  }

  for (const property of Object.values(properties)) {
    if (property.type !== "title") {
      continue;
    }

    return (property.title ?? []).map((segment) => segment.plain_text ?? "").join("");
  }

  return "";
}

export function mapNotionPageMetadata(page: NotionPageLike): PageMetadata {
  return {
    pageId: normalizePageId(page.id),
    title: deriveTitle(page.properties),
    notionUrl: page.url,
    publicUrl: page.public_url ?? null,
    lastEditedTime: page.last_edited_time
  };
}
