import { Fragment } from "react";
import { parseNotionPageReference } from "@/domain/notion-link";

type RichTextAnnotations = {
  bold?: boolean;
  italic?: boolean;
  strikethrough?: boolean;
  underline?: boolean;
  code?: boolean;
};

type RichTextToken = {
  plain_text?: string;
  href?: string | null;
  annotations?: RichTextAnnotations;
  text?: {
    content?: string;
    link?: {
      url?: string | null;
    } | null;
  };
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function asRichTextArray(value: unknown): RichTextToken[] {
  return Array.isArray(value) ? value.filter(isRecord) as RichTextToken[] : [];
}

function applyAnnotations(content: React.ReactNode, annotations?: RichTextAnnotations): React.ReactNode {
  if (!annotations) {
    return content;
  }

  let node = content;

  if (annotations.code) {
    node = <code>{node}</code>;
  }
  if (annotations.bold) {
    node = <strong>{node}</strong>;
  }
  if (annotations.italic) {
    node = <em>{node}</em>;
  }
  if (annotations.underline) {
    node = <u>{node}</u>;
  }
  if (annotations.strikethrough) {
    node = <s>{node}</s>;
  }

  return node;
}

export function rewriteNotionHref(href: string): string {
  const reference = parseNotionPageReference(href);
  return reference ? `/notion/${reference.pageId}` : href;
}

export function RichText({ richText }: { richText: unknown }): React.ReactNode {
  const tokens = asRichTextArray(richText);

  return tokens.map((token, index) => {
    const text = token.plain_text ?? token.text?.content ?? "";
    const rawHref = token.href ?? token.text?.link?.url ?? null;
    const content = applyAnnotations(text, token.annotations);

    if (!rawHref) {
      return <Fragment key={index}>{content}</Fragment>;
    }

    return (
      <a key={index} href={rewriteNotionHref(rawHref)}>
        {content}
      </a>
    );
  });
}
