import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

function renderChart(): string {
  return execFileSync(
    "helm",
    [
      "template",
      "notion-blog",
      "deploy/helm/notion-blog",
      "--set",
      "image.repository=notion-blog",
      "--set",
      "image.tag=test",
    ],
    {
      encoding: "utf8",
    },
  );
}

function getManifest(rendered: string, templateName: string): string {
  const sourceMarker = `# Source: notion-blog/templates/${templateName}`;
  const sourceIndex = rendered.indexOf(sourceMarker);

  if (sourceIndex === -1) {
    throw new Error(`Missing manifest for ${templateName}`);
  }

  const nextDocIndex = rendered.indexOf("\n---", sourceIndex + sourceMarker.length);
  return rendered.slice(sourceIndex, nextDocIndex === -1 ? undefined : nextDocIndex).trim();
}

describe("Helm chart", () => {
  it("keeps chart outside app source", () => {
    expect(existsSync("deploy/helm/notion-blog/Chart.yaml")).toBe(true);
  });

  it("references existing secret instead of creating database credentials", () => {
    const values = readFileSync("deploy/helm/notion-blog/values.yaml", "utf8");
    const rendered = renderChart();
    const webDeployment = getManifest(rendered, "web-deployment.yaml");
    const workerDeployment = getManifest(rendered, "worker-deployment.yaml");
    const migrationJob = getManifest(rendered, "migration-job.yaml");
    const service = getManifest(rendered, "service.yaml");

    expect(values).toContain("existingSecret:");
    expect(values).not.toContain("postgresql:");
    expect(rendered).not.toMatch(/kind:\s+Secret\b/);
    expect(rendered).not.toMatch(/postgresql/i);

    expect(webDeployment).toContain('name: "notion-blog-env"');
    expect(workerDeployment).toContain('name: "notion-blog-env"');
    expect(migrationJob).toContain('name: "notion-blog-env"');

    expect(workerDeployment).toContain('command: ["pnpm", "worker"]');
    expect(migrationJob).toContain('command: ["pnpm", "db:migrate"]');
    expect(migrationJob).toContain('"helm.sh/hook": pre-install,pre-upgrade');
    expect(migrationJob).toContain('"helm.sh/hook-delete-policy": before-hook-creation,hook-succeeded');

    expect(service).toContain("selector:");
    expect(service).toContain("app.kubernetes.io/name: notion-blog");
    expect(service).toContain("app.kubernetes.io/instance: notion-blog");
    expect(service).toContain("app.kubernetes.io/component: web");
  });
});

describe("DB-backed routes", () => {
  it("force dynamic rendering so Docker builds do not require a live database", () => {
    const homePage = readFileSync("src/app/page.tsx", "utf8");
    const slugPage = readFileSync("src/app/[slug]/page.tsx", "utf8");
    const layout = readFileSync("src/app/layout.tsx", "utf8");

    expect(homePage).toContain('export const dynamic = "force-dynamic"');
    expect(slugPage).toContain('export const dynamic = "force-dynamic"');
    expect(layout).toContain('export const dynamic = "force-dynamic"');
  });

  it("does not enable standalone output while one image also runs worker and migrations", () => {
    const nextConfig = readFileSync("next.config.ts", "utf8");

    expect(nextConfig).not.toContain('output: "standalone"');
  });
});
