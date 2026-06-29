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
        "claimDueRefreshTargets" | "upsertRefreshTarget" | "upsertSettingsSnapshot"
      >
    >;
  notion: NotionGateway;
  settingsDatabaseId?: string;
  now?: () => Date;
}

export interface SyncService {
  syncPage(pageId: string): Promise<void>;
  syncSettings(): Promise<ParsedSettings>;
  claimDueRefreshTargets(nowAt: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]>;
}

export function createSyncService({
  repository,
  notion,
  settingsDatabaseId,
  now
}: SyncServiceDependencies) {
  const pageService = createPageService({ repository, notion, now });
  const settingsService = settingsDatabaseId
    ? createSettingsService({
        repository: repository as Pick<BlogRepository, "upsertRefreshTarget" | "upsertSettingsSnapshot">,
        notion,
        settingsDatabaseId,
        now
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
    claimDueRefreshTargets(nowAt: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]> {
      if (!repository.claimDueRefreshTargets) {
        throw new Error("claimDueRefreshTargets is not configured");
      }

      return repository.claimDueRefreshTargets(nowAt, workerId, limit);
    }
  };

  return service;
}
