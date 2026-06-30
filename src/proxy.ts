import { NextResponse, type NextRequest } from "next/server";
import { getPageService } from "@/server/page-service";

function shouldBypassProxy(pathname: string): boolean {
  return pathname.startsWith("/notion/")
    || pathname.startsWith("/_next/")
    || /\/[^/]+\.[^/]+$/.test(pathname);
}

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (shouldBypassProxy(pathname)) {
    return NextResponse.next();
  }

  const pageService = await getPageService();
  const result = await pageService.getPageBySlug(pathname);

  if (result.kind === "redirect") {
    return NextResponse.redirect(new URL(result.destination, request.url), 301);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/|notion/|.*\\..*).*)"]
};
