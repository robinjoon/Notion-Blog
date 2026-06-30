import type { NotionBlockSnapshot } from "@/notion/block-collector";
import { renderNotionBlocks } from "@/components/notion/NotionBlockRenderer";

export function NotionPage({
  blocks,
  title
}: {
  blocks: NotionBlockSnapshot[];
  title: string;
}) {
  return (
    <article className="notion-page">
      <header>
        <h1 className="notion-heading-1">{title}</h1>
      </header>
      <section>{renderNotionBlocks(blocks)}</section>
    </article>
  );
}
