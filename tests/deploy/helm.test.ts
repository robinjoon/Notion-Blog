import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Helm chart", () => {
  it("keeps chart outside app source", () => {
    expect(existsSync("deploy/helm/notion-blog/Chart.yaml")).toBe(true);
  });

  it("references existing secret instead of creating database credentials", () => {
    const values = readFileSync("deploy/helm/notion-blog/values.yaml", "utf8");

    expect(values).toContain("existingSecret:");
    expect(values).not.toContain("postgresql:");
  });
});
