import type { SiteHeadSettings } from "@/domain/settings";
import { createSlug, createUniqueSlug } from "@/domain/slug";
import { RefreshTargetKind, SlugAliasStatus } from "@/generated/prisma/enums";
import type { PageSnapshot } from "@/notion/block-collector";

export type RouteResolution =
  | { kind: "page"; pageId: string; slug: string }
  | { kind: "redirect"; destination: string }
  | { kind: "not-found" };

export interface UpsertPageSnapshotInput extends PageSnapshot {}

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
  upsertRefreshTarget(target: RefreshTargetInput): Promise<void>;
  claimDueRefreshTargets(now: Date, workerId: string, limit: number): Promise<ClaimedRefreshTarget[]>;
  upsertSettingsSnapshot(input: UpsertSettingsSnapshotInput): Promise<void>;
}

type PrismaTransactionLike = {
  notionPage: {
    findUnique?: (args: unknown) => Promise<any>;
    upsert: (args: unknown) => Promise<unknown>;
    update?: (args: unknown) => Promise<unknown>;
  };
  pageRoute: {
    findUnique?: (args: unknown) => Promise<any>;
    findFirst?: (args: unknown) => Promise<any>;
    upsert: (args: unknown) => Promise<unknown>;
    updateMany?: (args: unknown) => Promise<unknown>;
  };
  slugAlias: {
    findUnique?: (args: unknown) => Promise<any>;
    findMany?: (args: unknown) => Promise<any[]>;
    upsert?: (args: unknown) => Promise<unknown>;
    updateMany?: (args: unknown) => Promise<unknown>;
  };
  pageSnapshot: {
    upsert: (args: unknown) => Promise<unknown>;
  };
  refreshTarget: {
    findMany?: (args: unknown) => Promise<RefreshTargetRecord[]>;
    upsert: (args: unknown) => Promise<unknown>;
    updateMany?: (args: unknown) => Promise<{ count: number }>;
  };
  siteSettings: {
    findUnique?: (args: unknown) => Promise<any>;
    upsert: (args: unknown) => Promise<unknown>;
  };
};

type PrismaLike = PrismaTransactionLike & {
  $transaction?: <T>(fn: (tx: PrismaTransactionLike) => Promise<T>) => Promise<T>;
};

function normalizeRefreshTargetKind(kind: "settings" | "page") {
  return kind === "settings" ? RefreshTargetKind.SETTINGS : RefreshTargetKind.PAGE;
}

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
        const now = new Date();

        await tx.notionPage.upsert({
          where: { pageId: input.pageId },
          update: {
            title: input.title,
            notionUrl: input.notionUrl,
            publicUrl: input.publicUrl,
            lastEditedTime: notionLastEditedTime,
            lastSyncedAt: now
          },
          create: {
            pageId: input.pageId,
            title: input.title,
            notionUrl: input.notionUrl,
            publicUrl: input.publicUrl,
            lastEditedTime: notionLastEditedTime,
            lastSyncedAt: now
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
            lastSyncedAt: now,
            failureCount: 0,
            lastError: null,
            lockedAt: null,
            lockedBy: null
          },
          create: {
            targetKind: RefreshTargetKind.PAGE,
            targetId: input.pageId,
            nextRefreshAt: now,
            lastSyncedAt: now,
            failureCount: 0
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

    async claimDueRefreshTargets(now, workerId, limit) {
      const dueTargets = await prisma.refreshTarget.findMany?.({
        where: {
          nextRefreshAt: { lte: now },
          lockedAt: null
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
            lockedAt: null
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
