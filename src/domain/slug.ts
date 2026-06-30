const NON_SLUG_CHARACTERS = /[^\p{Letter}\p{Number}]+/gu;
const LEADING_OR_TRAILING_HYPHENS = /^-+|-+$/g;

function normalizeBaseSlug(value: string): string {
  return value
    .toLowerCase()
    .replace(NON_SLUG_CHARACTERS, "-")
    .replace(LEADING_OR_TRAILING_HYPHENS, "");
}

function pageSuffix(pageId: string): string {
  return pageId.replace(/-/g, "").slice(0, 8);
}

export function createSlug(title: string, pageId?: string): string {
  const slug = normalizeBaseSlug(title);
  if (slug) {
    return slug;
  }

  const suffix = pageId ? pageSuffix(pageId) : "";
  return suffix ? `page-${suffix}` : "page";
}

export function createUniqueSlug(base: string, exists: (slug: string) => boolean, pageId: string): string {
  if (!exists(base)) {
    return base;
  }

  const suffix = pageSuffix(pageId);
  const candidate = suffix ? `${base}-${suffix}` : `${base}-page`;
  if (!exists(candidate)) {
    return candidate;
  }

  let counter = 2;
  while (exists(`${candidate}-${counter}`)) {
    counter += 1;
  }

  return `${candidate}-${counter}`;
}
