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
      await repository.completeRefreshTarget(target, now);
    } catch (error) {
      await repository.failRefreshTarget(target, error, now);
    }
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

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
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

  let stopping = false;
  process.on("SIGTERM", () => {
    stopping = true;
  });

  while (!stopping) {
    await runWorkerOnce({
      repository,
      syncSettings: () => syncService.syncSettings(),
      syncPage: (pageId) => syncService.syncPage(pageId),
      workerId,
      now: new Date()
    });

    if (stopping) {
      break;
    }

    await sleep(pollIntervalMs);
  }
}

if (import.meta.main) {
  startWorker().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
