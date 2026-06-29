import { NextResponse } from "next/server";
import { getPageService } from "@/server/page-service";

export async function GET(request: Request, { params }: { params: Promise<{ pageId: string }> }) {
  const { pageId } = await params;
  const pageService = await getPageService();
  const result = await pageService.collectLinkedPage(pageId);

  if (result.kind !== "redirect") {
    return new NextResponse(null, { status: 404 });
  }

  return NextResponse.redirect(new URL(result.destination, request.url));
}
