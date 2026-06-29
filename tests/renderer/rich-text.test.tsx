import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichText, rewriteNotionHref } from "@/components/notion/rich-text";

describe("rich text", () => {
  it("rewrites Notion page links to internal routes", () => {
    render(
      <RichText
        richText={[
          {
            type: "text",
            plain_text: "Internal page",
            href: "https://www.notion.so/My-Page-0123456789abcdef0123456789abcdef",
            annotations: {
              bold: false,
              italic: false,
              strikethrough: false,
              underline: false,
              code: false
            },
            text: { content: "Internal page", link: null }
          }
        ]}
      />
    );

    const anchor = screen.getByRole("link", { name: "Internal page" });
    expect(anchor.getAttribute("href")).toBe("/notion/0123456789abcdef0123456789abcdef");
  });

  it("leaves non-Notion links unchanged", () => {
    expect(rewriteNotionHref("https://example.com/post")).toBe("https://example.com/post");
  });
});
