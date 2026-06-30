import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NotionPage } from "@/components/notion/NotionPage";

describe("Notion renderer", () => {
  it("renders paragraphs and headings", () => {
    render(
      <NotionPage
        title="Hello"
        blocks={[
          {
            id: "h",
            type: "heading_1",
            hasChildren: false,
            data: { rich_text: [{ plain_text: "Title" }] },
            children: []
          },
          {
            id: "p",
            type: "paragraph",
            hasChildren: false,
            data: { rich_text: [{ plain_text: "Body" }] },
            children: []
          }
        ]}
      />
    );

    expect(screen.getByRole("heading", { name: "Title" })).toBeInTheDocument();
    expect(screen.getByText("Body")).toBeInTheDocument();
  });

  it("renders nested blocks recursively", () => {
    render(
      <NotionPage
        title="Hello"
        blocks={[
          {
            id: "toggle",
            type: "toggle",
            hasChildren: true,
            data: { rich_text: [{ plain_text: "More" }] },
            children: [
              {
                id: "quote",
                type: "quote",
                hasChildren: false,
                data: { rich_text: [{ plain_text: "Nested quote" }] },
                children: []
              }
            ]
          }
        ]}
      />
    );

    expect(screen.getByText("More")).toBeInTheDocument();
    expect(screen.getByText("Nested quote")).toBeInTheDocument();
  });

  it("rewrites bookmark links that point to Notion pages", () => {
    render(
      <NotionPage
        title="Hello"
        blocks={[
          {
            id: "bookmark",
            type: "bookmark",
            hasChildren: false,
            data: {
              url: "https://www.notion.so/My-Page-0123456789abcdef0123456789abcdef"
            },
            children: []
          }
        ]}
      />
    );

    expect(screen.getByRole("link")).toHaveAttribute(
      "href",
      "/notion/0123456789abcdef0123456789abcdef"
    );
  });

  it("renders emoji callout icons from structured icon objects", () => {
    render(
      <NotionPage
        title="Hello"
        blocks={[
          {
            id: "callout",
            type: "callout",
            hasChildren: false,
            data: {
              rich_text: [{ plain_text: "Heads up" }],
              icon: { type: "emoji", emoji: "💡" }
            },
            children: []
          }
        ]}
      />
    );

    expect(screen.getByText("💡")).toBeInTheDocument();
    expect(screen.getByText("Heads up")).toBeInTheDocument();
  });

  it("shows fallback for unsupported blocks", () => {
    render(
      <NotionPage
        title="Hello"
        blocks={[
          {
            id: "x",
            type: "unknown_block",
            hasChildren: false,
            data: {},
            children: []
          }
        ]}
      />
    );

    expect(screen.getByText("Unsupported Notion block: unknown_block")).toBeInTheDocument();
  });
});
