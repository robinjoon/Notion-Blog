import { z } from "zod";
import { parseNotionPageReference } from "@/domain/notion-link";

export interface SettingsRow {
  key: string;
  kind: "page" | "blocks" | "head";
  enabled: boolean;
  page: string;
  data: string;
}

export interface SiteHeadSettings {
  language?: string;
  siteName?: string;
  defaultTitle?: string;
  titleTemplate?: string;
  defaultDescription?: string;
  baseUrl?: string;
  logoUrl?: string;
  faviconUrl?: string;
  ogTitle?: string;
  ogDescription?: string;
  ogImageUrl?: string;
  ogType?: string;
  twitterCard?: string;
  twitterSite?: string;
  robots?: string;
  customCss?: string;
  customHeadHtml?: string;
}

export interface ParsedSettings {
  rootPageId: string;
  headerPageId?: string;
  footerPageId?: string;
  head: SiteHeadSettings;
}

const siteHeadSettingsSchema = z.object({
  language: z.string().optional(),
  siteName: z.string().optional(),
  defaultTitle: z.string().optional(),
  titleTemplate: z.string().optional(),
  defaultDescription: z.string().optional(),
  baseUrl: z.string().optional(),
  logoUrl: z.string().optional(),
  faviconUrl: z.string().optional(),
  ogTitle: z.string().optional(),
  ogDescription: z.string().optional(),
  ogImageUrl: z.string().optional(),
  ogType: z.string().optional(),
  twitterCard: z.string().optional(),
  twitterSite: z.string().optional(),
  robots: z.string().optional(),
  customCss: z.string().optional(),
  customHeadHtml: z.string().optional()
});

function parseHeadSettings(data: string): SiteHeadSettings {
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    throw new Error("settings head has invalid JSON");
  }

  try {
    return siteHeadSettingsSchema.parse(value);
  } catch {
    throw new Error("settings head has invalid JSON");
  }
}

function parsePageReference(page: string): string | null {
  return parseNotionPageReference(page)?.pageId ?? null;
}

function findRow(rows: SettingsRow[], key: string): SettingsRow | undefined {
  return rows.find((row) => row.enabled && row.key === key);
}

export function parseSettingsRows(rows: SettingsRow[]): ParsedSettings {
  const rootPageRow = findRow(rows, "rootPage");
  const rootPageId = rootPageRow ? parsePageReference(rootPageRow.page) : null;
  if (!rootPageId) {
    throw new Error("settings rootPage is required");
  }

  const headerPageId = findRow(rows, "header") ? parsePageReference(findRow(rows, "header")!.page) ?? undefined : undefined;
  const footerPageId = findRow(rows, "footer") ? parsePageReference(findRow(rows, "footer")!.page) ?? undefined : undefined;
  const headRow = findRow(rows, "head");

  return {
    rootPageId,
    headerPageId,
    footerPageId,
    head: headRow ? parseHeadSettings(headRow.data) : {}
  };
}
