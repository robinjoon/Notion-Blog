export type RefreshTargetKind = "settings" | "page";

export interface RefreshTarget {
  targetKind: RefreshTargetKind;
  targetId: string;
  failureCount: number;
  lastSyncedAt: Date | null;
}

const ONE_MINUTE_IN_MS = 60_000;
const FIVE_MINUTES_IN_MS = 5 * ONE_MINUTE_IN_MS;
const PAGE_REFRESH_IN_MS = 15 * ONE_MINUTE_IN_MS;

export function simpleRefreshPolicy(target: RefreshTarget, now: Date): Date {
  const baseDelay = target.targetKind === "settings" ? ONE_MINUTE_IN_MS : PAGE_REFRESH_IN_MS;
  const failureDelay = target.failureCount > 0 ? FIVE_MINUTES_IN_MS : 0;
  return new Date(now.getTime() + baseDelay + failureDelay);
}
