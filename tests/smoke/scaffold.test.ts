import { describe, expect, it } from "vitest";

describe("project scaffold", () => {
  it("uses the expected package name and Node runtime floor", async () => {
    const manifest = await import("../../package.json");
    expect(manifest.default.name).toBe("notion-blog");
    expect(manifest.default.engines.node).toBe(">=24.0.0");
  });
});
