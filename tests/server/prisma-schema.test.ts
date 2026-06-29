import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Prisma schema", () => {
  const schema = readFileSync("prisma/schema.prisma", "utf8");
  const config = readFileSync("prisma.config.ts", "utf8");

  it("uses the Prisma 7 generated client", () => {
    expect(schema).toContain('provider = "prisma-client"');
    expect(schema).toContain('output   = "../src/generated/prisma"');
    expect(schema).not.toMatch(/url\s*=\s*env\("DATABASE_URL"\)/);
    expect(config).toContain("defineConfig");
    expect(config).toContain('env("DATABASE_URL")');
  });

  it("defines publishing state models", () => {
    expect(schema).toContain("model SiteSettings");
    expect(schema).toContain("model NotionPage");
    expect(schema).toContain("model PageRoute");
    expect(schema).toContain("model SlugAlias");
    expect(schema).toContain("model PageSnapshot");
    expect(schema).toContain("model RefreshTarget");
    expect(schema).toContain("model SyncRun");
  });

  it("keeps canonical slugs and aliases unique", () => {
    expect(schema).toContain("canonicalSlug String     @unique");
    expect(schema).toContain("slug      String          @unique");
  });
});
