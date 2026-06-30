import type { ParsedSettings } from "@/domain/settings";
import type { NotionGateway } from "@/notion/gateway";
import { createPageService } from "@/server/page-service";
import type { BlogRepository, ClaimedRefreshTarget } from "@/server/repositories";
import { createSettingsService } from "@/server/settings-service";

interface SyncServiceDependencies {
  repository: Pick<BlogRepository, "markPagePrivate" | "upsertPageSnapshot"> &
    Partial<
      Pick<
        BlogRepository,
        "claimDueRefreshTargets" | "ensureRefreshTarget" | "upsertRefreshTarget" | "upsertSettingsSnapshot"
      >
    >;
  notion: NotionGateway;
  settingsDatabaseId?: string;
  now?: () => Date;
}

export interface SyncService {
  syncPage(pageId: string): Promise<void>;
  syncSettings(): Promise<ParsedSettings>;
  ensureSettingsRefreshTarget(): Promise<void>;
  claimDueRefreshTargets(nowAt: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]>;
}

export function createSyncService({
  repository,
  notion,
  settingsDatabaseId,
  now
}: SyncServiceDependencies) {
  const currentTime = now ?? (() => new Date());
  const pageService = createPageService({ repository, notion, now: currentTime });
  const settingsService = settingsDatabaseId
    ? createSettingsService({
        repository: repository as Pick<BlogRepository, "upsertRefreshTarget" | "upsertSettingsSnapshot">,
        notion,
        settingsDatabaseId,
        now: currentTime
      })
    : null;

  const service: SyncService = {
    syncPage: pageService.syncPage,
    async syncSettings() {
      if (!settingsService) {
        throw new Error("settingsDatabaseId is required");
      }

      return settingsService.syncSettings();
    },
    async ensureSettingsRefreshTarget(): Promise<void> {
      if (!settingsDatabaseId) {
        throw new Error("settingsDatabaseId is required");
      }

      if (!repository.ensureRefreshTarget) {
        throw new Error("ensureRefreshTarget is not configured");
      }

      await repository.ensureRefreshTarget({
        targetKind: "settings",
        targetId: settingsDatabaseId,
        nextRefreshAt: currentTime()
      });
    },
    claimDueRefreshTargets(nowAt: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]> {
      if (!repository.claimDueRefreshTargets) {
        throw new Error("claimDueRefreshTargets is not configured");
      }

      return repository.claimDueRefreshTargets(nowAt, workerId, limit);
    }
  };

  return service;
}
