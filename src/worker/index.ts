import { randomUUID } from "node:crypto";
import { createNotionGateway } from "@/notion/gateway";
import { createBlogRepository, type BlogRepository, type ClaimedRefreshTarget } from "@/server/repositories";
import { createSyncService } from "@/worker/sync-service";

interface WorkerDependencies {
  repository: Pick<BlogRepository, "claimDueRefreshTargets" | "completeRefreshTarget" | "failRefreshTarget">;
  syncSettings: () => Promise<unknown>;
  syncPage: (pageId: string) => Promise<void>;
  workerId: string;
  now: Date;
  limit?: number;
}

const DEFAULT_POLL_INTERVAL_MS = 5_000;
const DEFAULT_CLAIM_LIMIT = 10;

export interface ShutdownController {
  isStopping(): boolean;
  requestStop(): void;
  wait(ms: number): Promise<void>;
}

export function createShutdownController(): ShutdownController {
  let stopping = false;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let resolver: (() => void) | null = null;

  const clearPendingWait = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }

    if (resolver) {
      const resolve = resolver;
      resolver = null;
      resolve();
    }
  };

  return {
    isStopping() {
      return stopping;
    },
    requestStop() {
      if (stopping) {
        return;
      }

      stopping = true;
      clearPendingWait();
    },
    wait(ms: number) {
      if (stopping) {
        return Promise.resolve();
      }

      return new Promise((resolve) => {
        resolver = () => {
          resolver = null;
          resolve();
        };
        timer = setTimeout(() => {
          timer = null;
          const done = resolver;
          resolver = null;
          done?.();
        }, ms);
      });
    }
  };
}

export async function runWorkerOnce({
  repository,
  syncSettings,
  syncPage,
  workerId,
  now,
  limit = DEFAULT_CLAIM_LIMIT
}: WorkerDependencies): Promise<number> {
  const targets = await repository.claimDueRefreshTargets(now, workerId, limit);

  for (const target of targets) {
    try {
      await syncClaimedTarget(target, syncSettings, syncPage);
    } catch (error) {
      await repository.failRefreshTarget(target, error, now);
      continue;
    }

    await repository.completeRefreshTarget(target, now);
  }

  return targets.length;
}

async function syncClaimedTarget(
  target: ClaimedRefreshTarget,
  syncSettings: WorkerDependencies["syncSettings"],
  syncPage: WorkerDependencies["syncPage"]
) {
  if (target.targetKind === "settings") {
    await syncSettings();
    return;
  }

  await syncPage(target.targetId);
}

function parsePollIntervalMs(value: string | undefined): number {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : DEFAULT_POLL_INTERVAL_MS;
}

export async function startWorker(): Promise<void> {
  const notionToken = process.env.NOTION_TOKEN;
  if (!notionToken) {
    throw new Error("NOTION_TOKEN is required");
  }

  const settingsDatabaseId = process.env.SETTINGS_DATABASE_ID;
  if (!settingsDatabaseId) {
    throw new Error("SETTINGS_DATABASE_ID is required");
  }

  const { prisma } = await import("@/server/db");
  const repository = createBlogRepository(prisma);
  const syncService = createSyncService({
    repository,
    notion: createNotionGateway(notionToken),
    settingsDatabaseId
  });
  const pollIntervalMs = parsePollIntervalMs(process.env.WORKER_POLL_INTERVAL_MS);
  const workerId = process.env.WORKER_ID ?? randomUUID();
  const shutdown = createShutdownController();
  process.on("SIGTERM", () => {
    shutdown.requestStop();
  });

  while (!shutdown.isStopping()) {
    await runWorkerOnce({
      repository,
      syncSettings: () => syncService.syncSettings(),
      syncPage: (pageId) => syncService.syncPage(pageId),
      workerId,
      now: new Date()
    });

    if (shutdown.isStopping()) {
      break;
    }

    await shutdown.wait(pollIntervalMs);
  }
}

if (import.meta.main) {
  startWorker().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
