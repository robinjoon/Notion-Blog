import { notFound } from "next/navigation";
import { NotionPage } from "@/components/notion/NotionPage";
import { getPageService } from "@/server/page-service";

// Avoid build-time prerendering for DB-backed content during image builds.
export const dynamic = "force-dynamic";

export default async function HomePage() {
  const pageService = await getPageService();
  const result = await pageService.getRootPage();

  if (result.kind !== "page") {
    notFound();
  }

  return <NotionPage title={result.page.title} blocks={result.snapshot.blocks} />;
}
