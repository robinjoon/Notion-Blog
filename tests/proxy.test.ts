import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { proxy } from "@/proxy";
import { getPageService } from "@/server/page-service";

vi.mock("@/server/page-service", () => ({
  getPageService: vi.fn()
}));

describe("proxy redirects", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("returns a 301 for old slug aliases", async () => {
    vi.mocked(getPageService).mockResolvedValue({
      getPageBySlug: vi.fn().mockResolvedValue({ kind: "redirect", destination: "/new-slug" })
    } as never);

    const response = await proxy(new NextRequest("https://example.com/old-slug"));

    expect(response.status).toBe(301);
    expect(response.headers.get("location")).toBe("https://example.com/new-slug");
  });

  it("passes canonical, static, and notion routes through", async () => {
    const getPageBySlug = vi.fn().mockResolvedValue({ kind: "page" });
    vi.mocked(getPageService).mockResolvedValue({ getPageBySlug } as never);

    const canonicalResponse = await proxy(new NextRequest("https://example.com/current-slug"));
    const staticResponse = await proxy(new NextRequest("https://example.com/favicon.ico"));
    const notionResponse = await proxy(new NextRequest("https://example.com/notion/0123456789abcdef0123456789abcdef"));

    expect(canonicalResponse.headers.get("x-middleware-next")).toBe("1");
    expect(staticResponse.headers.get("x-middleware-next")).toBe("1");
    expect(notionResponse.headers.get("x-middleware-next")).toBe("1");
    expect(getPageBySlug).toHaveBeenCalledTimes(1);
    expect(getPageBySlug).toHaveBeenCalledWith("/current-slug");
  });
});
