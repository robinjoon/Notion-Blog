import type { SettingsRow } from "@/domain/settings";

type NormalizedSettingsRow = SettingsRow;

interface NotionSettingsProperty {
  type?: string;
  title?: Array<{ plain_text?: string | null }>;
  rich_text?: Array<{ plain_text?: string | null }>;
  select?: { name?: string | null } | null;
  checkbox?: boolean;
  url?: string | null;
}

interface RawNotionSettingsRow {
  properties?: Record<string, NotionSettingsProperty | undefined>;
}

function isSettingsRow(value: unknown): value is NormalizedSettingsRow {
  if (!value || typeof value !== "object") {
    return false;
  }

  const row = value as Record<string, unknown>;
  return typeof row.key === "string"
    && (row.kind === "page" || row.kind === "blocks" || row.kind === "head")
    && typeof row.enabled === "boolean"
    && typeof row.page === "string"
    && typeof row.data === "string";
}

function readPlainText(property: NotionSettingsProperty | undefined): string {
  if (!property) {
    return "";
  }

  if (property.type === "title") {
    return (property.title ?? []).map((item) => item.plain_text ?? "").join("");
  }

  if (property.type === "rich_text") {
    return (property.rich_text ?? []).map((item) => item.plain_text ?? "").join("");
  }

  if (property.type === "url") {
    return property.url ?? "";
  }

  if (property.type === "select") {
    return property.select?.name ?? "";
  }

  return "";
}

function mapRawSettingsRow(row: RawNotionSettingsRow): SettingsRow {
  const properties = row.properties ?? {};

  return {
    key: readPlainText(properties.Key),
    kind: readPlainText(properties.Kind) as SettingsRow["kind"],
    enabled: properties.Enabled?.type === "checkbox" ? Boolean(properties.Enabled.checkbox) : false,
    page: readPlainText(properties.Page),
    data: readPlainText(properties.Data)
  };
}

export function mapSettingsRows(rows: unknown[]): SettingsRow[] {
  if (rows.every(isSettingsRow)) {
    return rows;
  }

  return rows.map((row) => mapRawSettingsRow(row as RawNotionSettingsRow));
}
