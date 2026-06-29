import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Prisma schema", () => {
  const schema = readFileSync("prisma/schema.prisma", "utf8");
  const config = readFileSync("prisma.config.ts", "utf8");
  const dbClient = readFileSync("src/server/db.ts", "utf8");

  it("uses the Prisma 7 generated client", () => {
    expect(schema).toContain('provider = "prisma-client"');
    expect(schema).toContain('output   = "../src/generated/prisma"');
    expect(schema).not.toMatch(/url\s*=\s*env\("DATABASE_URL"\)/);
    expect(config).toContain("defineConfig");
    expect(config).toContain('env("DATABASE_URL")');
    expect(dbClient).toContain('import { PrismaClient } from "@/generated/prisma/client"');
    expect(dbClient).not.toContain('from "@prisma/client"');
    expect(dbClient).toContain('import { PrismaPg } from "@prisma/adapter-pg"');
    expect(dbClient).toContain("adapter: new PrismaPg({ connectionString })");
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

  it("models settings as an explicit singleton snapshot", () => {
    expect(schema).toMatch(/settingsDatabaseId\s+String\s+@unique/);
    expect(schema).toMatch(/rootPageId\s+String/);
    expect(schema).toMatch(/headerPageId\s+String\?/);
    expect(schema).toMatch(/footerPageId\s+String\?/);
    expect(schema).toMatch(/headJson\s+Json/);
    expect(schema).not.toContain("settingsJson");
    expect(schema).not.toContain("sourceId");
  });

  it("derives visibility and root routing instead of storing duplicate booleans", () => {
    expect(schema).toContain("publicUrl      String?");
    expect(schema).not.toContain("isPublic");
    expect(schema).not.toContain("isRoot");
  });

  it("keeps canonical slugs and aliases unique", () => {
    expect(schema).toContain("canonicalSlug String     @unique");
    expect(schema).toContain("slug      String          @unique");
  });
});
