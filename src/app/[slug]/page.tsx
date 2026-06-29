import { notFound, redirect } from "next/navigation";
import { NotionPage } from "@/components/notion/NotionPage";
import { getPageService } from "@/server/page-service";

export default async function BlogPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const pageService = await getPageService();
  const result = await pageService.getPageBySlug(slug);

  if (result.kind === "redirect") {
    redirect(result.destination);
  }

  if (result.kind !== "page") {
    notFound();
  }

  return <NotionPage title={result.page.title} blocks={result.snapshot.blocks} />;
}
