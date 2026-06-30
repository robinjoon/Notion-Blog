import { notFound, permanentRedirect } from "next/navigation";
import { NotionPage } from "@/components/notion/NotionPage";
import { getPageService } from "@/server/page-service";

// Avoid build-time prerendering for DB-backed content during image builds.
export const dynamic = "force-dynamic";

export default async function BlogPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const pageService = await getPageService();
  const result = await pageService.getPageBySlug(slug);

  if (result.kind === "redirect") {
    permanentRedirect(result.destination);
  }

  if (result.kind !== "page") {
    notFound();
  }

  return <NotionPage title={result.page.title} blocks={result.snapshot.blocks} />;
}
