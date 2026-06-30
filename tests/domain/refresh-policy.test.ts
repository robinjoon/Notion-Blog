import { describe, expect, it } from "vitest";
import { simpleRefreshPolicy } from "@/domain/refresh-policy";

describe("simple refresh policy", () => {
  const now = new Date("2026-06-29T00:00:00.000Z");

  it("refreshes settings after one minute", () => {
    expect(simpleRefreshPolicy({ targetKind: "settings", targetId: "settings", failureCount: 0, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:01:00.000Z");
  });

  it("refreshes pages after fifteen minutes", () => {
    expect(simpleRefreshPolicy({ targetKind: "page", targetId: "page-a", failureCount: 0, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:15:00.000Z");
  });

  it("backs off failed targets", () => {
    expect(simpleRefreshPolicy({ targetKind: "page", targetId: "page-a", failureCount: 2, lastSyncedAt: null }, now).toISOString()).toBe("2026-06-29T00:20:00.000Z");
  });
});
