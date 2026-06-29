import { describe, expect, it } from "vitest";
import { createSlug, createUniqueSlug } from "@/domain/slug";

describe("slug domain", () => {
  it("keeps Korean text readable", () => {
    expect(createSlug("첫 번째 글")).toBe("첫-번째-글");
  });

  it("normalizes latin punctuation", () => {
    expect(createSlug("Next.js Cache Notes!")).toBe("next-js-cache-notes");
  });

  it("falls back to page id when title has no slug characters", () => {
    expect(createSlug("!!!", "1234567890abcdef")).toBe("page-12345678");
  });

  it("adds a stable suffix when slug already exists", () => {
    const taken = new Set(["hello"]);
    expect(createUniqueSlug("hello", (slug) => taken.has(slug), "abcdef123456")).toBe("hello-abcdef12");
  });
});
