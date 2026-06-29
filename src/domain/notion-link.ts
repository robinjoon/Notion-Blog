const NOTION_HOST_SUFFIX = "notion.so";
const NOTION_ID_PATTERN = /([0-9a-f]{32}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})/i;

function normalizePageId(pageId: string): string | null {
  const normalized = pageId.replace(/-/g, "").toLowerCase();
  return /^[0-9a-f]{32}$/.test(normalized) ? normalized : null;
}

export function parseNotionPageReference(input: string): { pageId: string } | null {
  const trimmed = input.trim();
  const rawPageId = normalizePageId(trimmed);
  if (rawPageId) {
    return { pageId: rawPageId };
  }

  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return null;
  }

  if (!(url.hostname === NOTION_HOST_SUFFIX || url.hostname.endsWith(`.${NOTION_HOST_SUFFIX}`))) {
    return null;
  }

  const match = trimmed.match(NOTION_ID_PATTERN);
  if (!match) {
    return null;
  }

  const pageId = normalizePageId(match[1]);
  return pageId ? { pageId } : null;
}
