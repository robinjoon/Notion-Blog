import { parseSettingsRows, type ParsedSettings } from "@/domain/settings";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";
import type { NotionGateway } from "@/notion/gateway";
import type { BlogRepository } from "@/server/repositories";

interface SettingsServiceDependencies {
  notion: NotionGateway;
  repository: Pick<BlogRepository, "upsertRefreshTarget" | "upsertRootRoute" | "upsertSettingsSnapshot">;
  settingsDatabaseId: string;
  now?: () => Date;
}

export function createSettingsService({
  notion,
  repository,
  settingsDatabaseId,
  now = () => new Date()
}: SettingsServiceDependencies) {
  return {
    async syncSettings(): Promise<ParsedSettings> {
      const rows = await notion.querySettingsDatabase(settingsDatabaseId);
      const parsed = parseSettingsRows(rows as Parameters<typeof parseSettingsRows>[0]);
      const syncedAt = now();

      await repository.upsertSettingsSnapshot({
        settingsDatabaseId,
        rootPageId: parsed.rootPageId,
        headerPageId: parsed.headerPageId,
        footerPageId: parsed.footerPageId,
        headJson: parsed.head
      });

      await repository.upsertRootRoute(parsed.rootPageId);
      await repository.upsertRefreshTarget({
        targetKind: "settings",
        targetId: settingsDatabaseId,
        nextRefreshAt: simpleRefreshPolicy(
          { targetKind: "settings", targetId: settingsDatabaseId, failureCount: 0, lastSyncedAt: syncedAt },
          syncedAt
        ),
        lastSyncedAt: syncedAt,
        failureCount: 0,
        lastError: null,
        lockedAt: null,
        lockedBy: null
      });

      const pageIds = [parsed.rootPageId, parsed.headerPageId, parsed.footerPageId].filter(
        (value): value is string => Boolean(value)
      );

      for (const pageId of pageIds) {
        await repository.upsertRefreshTarget({
          targetKind: "page",
          targetId: pageId,
          nextRefreshAt: syncedAt,
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: null,
          lockedBy: null
        });
      }

      return parsed;
    }
  };
}
