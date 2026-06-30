const NOTION_HOST_SUFFIXES = ["notion.so", "notion.site"];
const NOTION_ID_PATTERN = /([0-9a-f]{32}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})/i;

export function normalizeNotionPageId(pageId: string): string | null {
  const normalized = pageId.replace(/-/g, "").toLowerCase();
  return /^[0-9a-f]{32}$/.test(normalized) ? normalized : null;
}

export function parseNotionPageReference(input: string): { pageId: string } | null {
  const trimmed = input.trim();
  const rawPageId = normalizeNotionPageId(trimmed);
  if (rawPageId) {
    return { pageId: rawPageId };
  }

  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return null;
  }

  if (!NOTION_HOST_SUFFIXES.some((suffix) => url.hostname === suffix || url.hostname.endsWith(`.${suffix}`))) {
    return null;
  }

  const match = trimmed.match(NOTION_ID_PATTERN);
  if (!match) {
    return null;
  }

  const pageId = normalizeNotionPageId(match[1]);
  return pageId ? { pageId } : null;
}
