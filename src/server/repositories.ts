import type { SiteHeadSettings } from "@/domain/settings";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";
import { createSlug, createUniqueSlug } from "@/domain/slug";
import { RefreshTargetKind, SlugAliasStatus } from "@/generated/prisma/enums";
import type { PageSnapshot } from "@/notion/block-collector";

export type RouteResolution =
  | { kind: "page"; pageId: string; slug: string }
  | { kind: "redirect"; destination: string }
  | { kind: "not-found" };

export interface UpsertPageSnapshotInput extends PageSnapshot {
  syncedAt: Date;
  nextRefreshAt: Date;
}

export interface UpsertSettingsSnapshotInput {
  settingsDatabaseId: string;
  rootPageId: string;
  headerPageId?: string;
  footerPageId?: string;
  headJson: SiteHeadSettings;
}

export interface MarkPagePrivateInput {
  pageId: string;
  syncedAt: Date;
  nextRefreshAt: Date;
}

export interface RefreshTargetInput {
  targetKind: "settings" | "page";
  targetId: string;
  nextRefreshAt: Date;
  lastSyncedAt?: Date | null;
  failureCount?: number;
  lastError?: string | null;
  lockedAt?: Date | null;
  lockedBy?: string | null;
}

export interface ClaimedRefreshTarget extends RefreshTargetInput {
  lastSyncedAt: Date | null;
  failureCount: number;
  lastError: string | null;
  lockedAt: Date | null;
  lockedBy: string | null;
}

export interface RefreshTargetRecord {
  targetKind: "SETTINGS" | "PAGE";
  targetId: string;
  nextRefreshAt: Date;
  lastSyncedAt: Date | null;
  failureCount: number;
  lastError: string | null;
  lockedAt: Date | null;
  lockedBy: string | null;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface BlogRepository {
  upsertPageSnapshot(input: UpsertPageSnapshotInput): Promise<void>;
  markPagePrivate(input: MarkPagePrivateInput): Promise<void>;
  resolveRoute(slug: string): Promise<RouteResolution>;
  getPageContent(pageId: string): Promise<CachedPageContent | null>;
  getSiteSettings(settingsDatabaseId: string): Promise<SiteSettingsSnapshot | null>;
  ensureRefreshTarget(target: Pick<RefreshTargetInput, "targetKind" | "targetId" | "nextRefreshAt">): Promise<void>;
  upsertRefreshTarget(target: RefreshTargetInput): Promise<void>;
  claimDueRefreshTargets(now: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]>;
  completeRefreshTarget(target: ClaimedRefreshTarget, now: Date): Promise<void>;
  failRefreshTarget(target: ClaimedRefreshTarget, error: unknown, now: Date): Promise<void>;
  upsertSettingsSnapshot(input: UpsertSettingsSnapshotInput): Promise<void>;
}

export interface CachedPageContent {
  pageId: string;
  title: string;
  notionUrl: string;
  publicUrl: string;
  lastEditedTime: Date | null;
  slug: string;
  snapshot: PageSnapshot;
}

export interface SiteSettingsSnapshot {
  settingsDatabaseId: string;
  rootPageId: string;
  headerPageId?: string;
  footerPageId?: string;
  head: SiteHeadSettings;
}

type PrismaTransactionLike = {
  notionPage: {
    findUnique?: (...args: any[]) => Promise<any>;
    upsert: (...args: any[]) => Promise<unknown>;
    update?: (...args: any[]) => Promise<unknown>;
  };
  pageRoute: {
    findUnique?: (...args: any[]) => Promise<any>;
    findFirst?: (...args: any[]) => Promise<any>;
    upsert: (...args: any[]) => Promise<unknown>;
    updateMany?: (...args: any[]) => Promise<unknown>;
  };
  slugAlias: {
    findUnique?: (...args: any[]) => Promise<any>;
    findMany?: (...args: any[]) => Promise<any[]>;
    upsert?: (...args: any[]) => Promise<unknown>;
    updateMany?: (...args: any[]) => Promise<unknown>;
  };
  pageSnapshot: {
    findUnique?: (...args: any[]) => Promise<any>;
    upsert: (...args: any[]) => Promise<unknown>;
  };
  refreshTarget: {
    findMany?: (...args: any[]) => Promise<RefreshTargetRecord[]>;
    upsert: (...args: any[]) => Promise<unknown>;
    updateMany?: (...args: any[]) => Promise<{ count: number }>;
  };
  siteSettings: {
    findUnique?: (...args: any[]) => Promise<any>;
    upsert: (...args: any[]) => Promise<unknown>;
  };
};

type PrismaLike = PrismaTransactionLike & {
  $transaction?: <T>(fn: (tx: PrismaTransactionLike) => Promise<T>) => Promise<T>;
};

function normalizeRefreshTargetKind(kind: "settings" | "page") {
  return kind === "settings" ? RefreshTargetKind.SETTINGS : RefreshTargetKind.PAGE;
}

const REFRESH_TARGET_LOCK_TTL_MS = 5 * 60 * 1000;

function toClaimedRefreshTarget(record: RefreshTargetRecord): ClaimedRefreshTarget {
  return {
    targetKind: record.targetKind === RefreshTargetKind.SETTINGS ? "settings" : "page",
    targetId: record.targetId,
    nextRefreshAt: record.nextRefreshAt,
    lastSyncedAt: record.lastSyncedAt,
    failureCount: record.failureCount,
    lastError: record.lastError,
    lockedAt: record.lockedAt,
    lockedBy: record.lockedBy
  };
}

async function withTransaction<T>(prisma: PrismaLike, work: (tx: PrismaTransactionLike) => Promise<T>): Promise<T> {
  if (prisma.$transaction) {
    return prisma.$transaction(work);
  }

  return work(prisma);
}

async function findExistingSlugs(tx: PrismaTransactionLike, pageId: string): Promise<Set<string>> {
  const aliases = await tx.slugAlias.findMany?.({ where: { pageId } });
  return new Set((aliases ?? []).map((alias) => alias.slug));
}

async function slugExists(tx: PrismaTransactionLike, slug: string, pageId: string): Promise<boolean> {
  const route = await tx.pageRoute.findUnique?.({ where: { canonicalSlug: slug } });
  if (route && route.pageId !== pageId) {
    return true;
  }

  const alias = await tx.slugAlias.findUnique?.({ where: { slug } });
  return Boolean(alias && alias.pageId !== pageId);
}

function fallbackRootSlug(pageId: string): string {
  return `/${createSlug("", pageId)}`;
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === "string") {
    return error;
  }

  return "Unknown worker error";
}

async function ensureCanonicalSlug(tx: PrismaTransactionLike, pageId: string, title: string): Promise<string> {
  const existingRoute = await tx.pageRoute.findUnique?.({ where: { pageId } });
  if (existingRoute?.canonicalSlug === "/") {
    return "/";
  }

  const baseSlug = `/${createSlug(title, pageId)}`;
  const currentSlug = existingRoute?.canonicalSlug;
  const existingSlugs = await findExistingSlugs(tx, pageId);
  const nextSlug = currentSlug && (currentSlug === baseSlug || existingSlugs.has(currentSlug))
    ? currentSlug
    : `/${createUniqueSlug(baseSlug.slice(1), (candidate) => candidate !== currentSlug && false, pageId)}`;

  const canonicalSlug = currentSlug === baseSlug
    ? currentSlug
    : await (async () => {
        if (!(await slugExists(tx, baseSlug, pageId))) {
          return baseSlug;
        }

        let candidate = nextSlug;
        let counter = 2;
        while (await slugExists(tx, candidate, pageId)) {
          candidate = `${baseSlug}-${counter}`;
          counter += 1;
        }
        return candidate;
      })();

  if (currentSlug && currentSlug !== canonicalSlug) {
    await tx.slugAlias.upsert?.({
      where: { slug: currentSlug },
      update: {
        pageId,
        status: SlugAliasStatus.ACTIVE
      },
      create: {
        pageId,
        slug: currentSlug,
        status: SlugAliasStatus.ACTIVE
      }
    });
  }

  return canonicalSlug;
}

export function createBlogRepository(prisma: PrismaLike): BlogRepository {
  return {
    async upsertPageSnapshot(input) {
      await withTransaction(prisma, async (tx) => {
        const canonicalSlug = await ensureCanonicalSlug(tx, input.pageId, input.title);
        const notionLastEditedTime = new Date(input.lastEditedTime);

        await tx.notionPage.upsert({
          where: { pageId: input.pageId },
          update: {
            title: input.title,
            notionUrl: input.notionUrl,
            publicUrl: input.publicUrl,
            lastEditedTime: notionLastEditedTime,
            lastSyncedAt: input.syncedAt
          },
          create: {
            pageId: input.pageId,
            title: input.title,
            notionUrl: input.notionUrl,
            publicUrl: input.publicUrl,
            lastEditedTime: notionLastEditedTime,
            lastSyncedAt: input.syncedAt
          }
        });

        await tx.pageRoute.upsert({
          where: { pageId: input.pageId },
          update: {
            canonicalSlug,
            isActive: true
          },
          create: {
            pageId: input.pageId,
            canonicalSlug,
            isActive: true
          }
        });

        await tx.pageSnapshot.upsert({
          where: { pageId: input.pageId },
          update: {
            snapshotJson: input,
            notionLastEditedTime
          },
          create: {
            pageId: input.pageId,
            snapshotJson: input,
            notionLastEditedTime
          }
        });

        await tx.refreshTarget.upsert({
          where: {
            targetKind_targetId: {
              targetKind: RefreshTargetKind.PAGE,
              targetId: input.pageId
            }
          },
          update: {
            nextRefreshAt: input.nextRefreshAt,
            lastSyncedAt: input.syncedAt,
            failureCount: 0,
            lastError: null,
            lockedAt: null,
            lockedBy: null
          },
          create: {
            targetKind: RefreshTargetKind.PAGE,
            targetId: input.pageId,
            nextRefreshAt: input.nextRefreshAt,
            lastSyncedAt: input.syncedAt,
            failureCount: 0,
            lastError: null,
            lockedAt: null,
            lockedBy: null
          }
        });
      });
    },

    async markPagePrivate(input) {
      await withTransaction(prisma, async (tx) => {
        await tx.notionPage.upsert({
          where: { pageId: input.pageId },
          update: {
            publicUrl: null,
            lastSyncedAt: input.syncedAt
          },
          create: {
            pageId: input.pageId,
            title: "",
            notionUrl: "",
            publicUrl: null,
            lastSyncedAt: input.syncedAt
          }
        });

        await tx.pageRoute.updateMany?.({
          where: { pageId: input.pageId },
          data: { isActive: false }
        });

        await tx.slugAlias.updateMany?.({
          where: { pageId: input.pageId, status: SlugAliasStatus.ACTIVE },
          data: { status: SlugAliasStatus.INACTIVE }
        });

        await tx.refreshTarget.upsert({
          where: {
            targetKind_targetId: {
              targetKind: RefreshTargetKind.PAGE,
              targetId: input.pageId
            }
          },
          update: {
            nextRefreshAt: input.nextRefreshAt,
            lastSyncedAt: input.syncedAt,
            failureCount: 0,
            lastError: null,
            lockedAt: null,
            lockedBy: null
          },
          create: {
            targetKind: RefreshTargetKind.PAGE,
            targetId: input.pageId,
            nextRefreshAt: input.nextRefreshAt,
            lastSyncedAt: input.syncedAt,
            failureCount: 0,
            lastError: null,
            lockedAt: null,
            lockedBy: null
          }
        });
      });
    },

    async resolveRoute(slug) {
      const route = await prisma.pageRoute.findUnique?.({
        where: { canonicalSlug: slug }
      });
      if (route?.isActive) {
        return { kind: "page", pageId: route.pageId, slug: route.canonicalSlug };
      }

      const alias = await prisma.slugAlias.findUnique?.({
        where: { slug },
        include: {
          page: {
            include: {
              route: true
            }
          }
        }
      });

      if (alias?.status === SlugAliasStatus.ACTIVE && alias.page?.route?.isActive) {
        return { kind: "redirect", destination: alias.page.route.canonicalSlug };
      }

      return { kind: "not-found" };
    },

    async getPageContent(pageId) {
      const page = await prisma.notionPage.findUnique?.({
        where: { pageId },
        include: {
          route: true,
          snapshot: true
        }
      });

      if (!page?.publicUrl || !page.route?.isActive || !page.snapshot?.snapshotJson) {
        return null;
      }

      return {
        pageId: page.pageId,
        title: page.title,
        notionUrl: page.notionUrl,
        publicUrl: page.publicUrl,
        lastEditedTime: page.lastEditedTime ?? null,
        slug: page.route.canonicalSlug,
        snapshot: page.snapshot.snapshotJson as PageSnapshot
      };
    },

    async getSiteSettings(settingsDatabaseId) {
      const settings = await prisma.siteSettings.findUnique?.({
        where: { settingsDatabaseId }
      });

      if (!settings) {
        return null;
      }

      return {
        settingsDatabaseId: settings.settingsDatabaseId,
        rootPageId: settings.rootPageId,
        headerPageId: settings.headerPageId ?? undefined,
        footerPageId: settings.footerPageId ?? undefined,
        head: settings.headJson as SiteHeadSettings
      };
    },

    async upsertRefreshTarget(target) {
      await prisma.refreshTarget.upsert({
        where: {
          targetKind_targetId: {
            targetKind: normalizeRefreshTargetKind(target.targetKind),
            targetId: target.targetId
          }
        },
        update: {
          nextRefreshAt: target.nextRefreshAt,
          lastSyncedAt: target.lastSyncedAt ?? null,
          failureCount: target.failureCount ?? 0,
          lastError: target.lastError ?? null,
          lockedAt: target.lockedAt ?? null,
          lockedBy: target.lockedBy ?? null
        },
        create: {
          targetKind: normalizeRefreshTargetKind(target.targetKind),
          targetId: target.targetId,
          nextRefreshAt: target.nextRefreshAt,
          lastSyncedAt: target.lastSyncedAt ?? null,
          failureCount: target.failureCount ?? 0,
          lastError: target.lastError ?? null,
          lockedAt: target.lockedAt ?? null,
          lockedBy: target.lockedBy ?? null
        }
      });
    },

    async ensureRefreshTarget(target) {
      await prisma.refreshTarget.upsert({
        where: {
          targetKind_targetId: {
            targetKind: normalizeRefreshTargetKind(target.targetKind),
            targetId: target.targetId
          }
        },
        update: {},
        create: {
          targetKind: normalizeRefreshTargetKind(target.targetKind),
          targetId: target.targetId,
          nextRefreshAt: target.nextRefreshAt,
          lastSyncedAt: null,
          failureCount: 0,
          lastError: null,
          lockedAt: null,
          lockedBy: null
        }
      });
    },

    async claimDueRefreshTargets(now, workerId, limit) {
      const staleLockCutoff = new Date(now.getTime() - REFRESH_TARGET_LOCK_TTL_MS);
      const dueTargets = await prisma.refreshTarget.findMany?.({
        where: {
          nextRefreshAt: { lte: now },
          OR: [
            { lockedAt: null },
            { lockedAt: { lt: staleLockCutoff } }
          ]
        },
        orderBy: { nextRefreshAt: "asc" },
        take: limit
      });

      const claimed: ClaimedRefreshTarget[] = [];

      for (const target of dueTargets ?? []) {
        const result = await prisma.refreshTarget.updateMany?.({
          where: {
            targetKind: target.targetKind,
            targetId: target.targetId,
            OR: [
              { lockedAt: null },
              { lockedAt: { lt: staleLockCutoff } }
            ]
          },
          data: {
            lockedAt: now,
            lockedBy: workerId
          }
        });

        if (!result || result.count === 0) {
          continue;
        }

        const lockedTargets = await prisma.refreshTarget.findMany?.({
          where: {
            targetKind: target.targetKind,
            targetId: target.targetId,
            lockedAt: now,
            lockedBy: workerId
          },
          take: 1
        });

        const lockedTarget = lockedTargets?.[0];
        if (lockedTarget) {
          claimed.push(toClaimedRefreshTarget(lockedTarget));
        }
      }

      return claimed;
    },

    async completeRefreshTarget(target, now) {
      await prisma.refreshTarget.updateMany?.({
        where: {
          targetKind: normalizeRefreshTargetKind(target.targetKind),
          targetId: target.targetId,
          lockedAt: target.lockedAt,
          lockedBy: target.lockedBy
        },
        data: {
          nextRefreshAt: simpleRefreshPolicy(
            {
              targetKind: target.targetKind,
              targetId: target.targetId,
              failureCount: 0,
              lastSyncedAt: now
            },
            now
          ),
          lastSyncedAt: now,
          failureCount: 0,
          lastError: null,
          lockedAt: null,
          lockedBy: null
        }
      });
    },

    async failRefreshTarget(target, error, now) {
      const failureCount = target.failureCount + 1;

      await prisma.refreshTarget.updateMany?.({
        where: {
          targetKind: normalizeRefreshTargetKind(target.targetKind),
          targetId: target.targetId,
          lockedAt: target.lockedAt,
          lockedBy: target.lockedBy
        },
        data: {
          nextRefreshAt: simpleRefreshPolicy(
            {
              targetKind: target.targetKind,
              targetId: target.targetId,
              failureCount,
              lastSyncedAt: target.lastSyncedAt
            },
            now
          ),
          lastSyncedAt: target.lastSyncedAt,
          failureCount,
          lastError: errorMessage(error),
          lockedAt: null,
          lockedBy: null
        }
      });
    },

    async upsertSettingsSnapshot(input) {
      await withTransaction(prisma, async (tx) => {
        const syncedAt = new Date();

        await tx.notionPage.upsert({
          where: { pageId: input.rootPageId },
          update: {},
          create: {
            pageId: input.rootPageId,
            title: "",
            notionUrl: "",
            publicUrl: null
          }
        });

        const currentRootRoute = await tx.pageRoute.findUnique?.({
          where: { canonicalSlug: "/" }
        });
        if (currentRootRoute && currentRootRoute.pageId !== input.rootPageId) {
          await tx.pageRoute.updateMany?.({
            where: {
              pageId: currentRootRoute.pageId,
              canonicalSlug: "/"
            },
            data: {
              canonicalSlug: fallbackRootSlug(currentRootRoute.pageId)
            }
          });
        }

        const targetRootRoute = await tx.pageRoute.findUnique?.({
          where: { pageId: input.rootPageId }
        });
        if (targetRootRoute?.canonicalSlug && targetRootRoute.canonicalSlug !== "/") {
          await tx.slugAlias.upsert?.({
            where: { slug: targetRootRoute.canonicalSlug },
            update: {
              pageId: input.rootPageId,
              status: SlugAliasStatus.ACTIVE
            },
            create: {
              pageId: input.rootPageId,
              slug: targetRootRoute.canonicalSlug,
              status: SlugAliasStatus.ACTIVE
            }
          });
        }

        await tx.pageRoute.upsert({
          where: { pageId: input.rootPageId },
          update: {
            canonicalSlug: "/",
            isActive: true
          },
          create: {
            pageId: input.rootPageId,
            canonicalSlug: "/",
            isActive: true
          }
        });

        await tx.siteSettings.upsert({
          where: { settingsDatabaseId: input.settingsDatabaseId },
          update: {
            rootPageId: input.rootPageId,
            headerPageId: input.headerPageId ?? null,
            footerPageId: input.footerPageId ?? null,
            headJson: input.headJson,
            lastSyncedAt: syncedAt
          },
          create: {
            settingsDatabaseId: input.settingsDatabaseId,
            rootPageId: input.rootPageId,
            headerPageId: input.headerPageId ?? null,
            footerPageId: input.footerPageId ?? null,
            headJson: input.headJson,
            lastSyncedAt: syncedAt
          }
        });
      });
    }
  };
}
