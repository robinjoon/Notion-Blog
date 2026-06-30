import type { NotionBlockSnapshot } from "@/notion/block-collector";
import { RichText, rewriteNotionHref } from "@/components/notion/rich-text";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function getBlockPayload(block: NotionBlockSnapshot): Record<string, unknown> {
  if (isRecord(block.data[block.type])) {
    return block.data[block.type] as Record<string, unknown>;
  }

  return block.data;
}

function getRichText(block: NotionBlockSnapshot): unknown[] {
  const payload = getBlockPayload(block);
  return Array.isArray(payload.rich_text) ? payload.rich_text : [];
}

function getCaption(payload: Record<string, unknown>): unknown[] {
  return Array.isArray(payload.caption) ? payload.caption : [];
}

function getFileUrl(payload: Record<string, unknown>): string | null {
  const file = isRecord(payload.file) ? payload.file : null;
  if (typeof file?.url === "string") {
    return file.url;
  }

  const external = isRecord(payload.external) ? payload.external : null;
  if (typeof external?.url === "string") {
    return external.url;
  }

  const url = payload.url;
  return typeof url === "string" ? url : null;
}

function renderChildren(block: NotionBlockSnapshot): React.ReactNode {
  if (block.children.length === 0) {
    return null;
  }

  return <div className="notion-children">{renderNotionBlocks(block.children)}</div>;
}

function renderListBlock(block: NotionBlockSnapshot): React.ReactNode {
  const children = renderChildren(block);

  if (block.type === "to_do") {
    const payload = getBlockPayload(block);
    const checked = Boolean(payload.checked);

    return (
      <>
        <label className="notion-block notion-to-do">
          <input type="checkbox" checked={checked} readOnly aria-label="Notion to-do item" />
          <span>
            <RichText richText={getRichText(block)} />
          </span>
        </label>
        {children}
      </>
    );
  }

  return (
    <>
      <div className="notion-block">
        <RichText richText={getRichText(block)} />
      </div>
      {children}
    </>
  );
}

function renderTable(block: NotionBlockSnapshot): React.ReactNode {
  return (
    <div className="notion-block notion-table-wrapper">
      <table className="notion-table">
        <tbody>
          {block.children.map((row) => {
            const payload = getBlockPayload(row);
            const cells = Array.isArray(payload.cells) ? payload.cells : [];

            return (
              <tr key={row.id}>
                {cells.map((cell, index) => (
                  <td key={`${row.id}-${index}`}>
                    <RichText richText={cell} />
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function renderMedia(block: NotionBlockSnapshot, className: string, label: string): React.ReactNode {
  const payload = getBlockPayload(block);
  const url = getFileUrl(payload);
  const caption = getCaption(payload);

  return (
    <figure className={`notion-block ${className}`}>
      {block.type === "image" && url ? <img src={url} alt="" className="notion-image-element" /> : null}
      {block.type !== "image" && url ? (
        <a href={url}>
          {label}
        </a>
      ) : null}
      {caption.length > 0 ? (
        <figcaption>
          <RichText richText={caption} />
        </figcaption>
      ) : null}
    </figure>
  );
}

function renderCalloutIcon(icon: unknown): React.ReactNode {
  if (typeof icon === "string" && icon.length > 0) {
    return icon;
  }

  if (isRecord(icon) && icon.type === "emoji" && typeof icon.emoji === "string" && icon.emoji.length > 0) {
    return icon.emoji;
  }

  return "!";
}

function renderHeading(
  block: NotionBlockSnapshot,
  level: 1 | 2 | 3
): React.ReactNode {
  const className = `notion-block notion-heading-${level}`;
  const content = <RichText richText={getRichText(block)} />;

  if (block.children.length === 0) {
    if (level === 1) {
      return <h1 className={className}>{content}</h1>;
    }
    if (level === 2) {
      return <h2 className={className}>{content}</h2>;
    }
    return <h3 className={className}>{content}</h3>;
  }

  return (
    <div className="notion-heading-group">
      {level === 1 ? <h1 className={className}>{content}</h1> : null}
      {level === 2 ? <h2 className={className}>{content}</h2> : null}
      {level === 3 ? <h3 className={className}>{content}</h3> : null}
      {renderChildren(block)}
    </div>
  );
}

export function NotionBlockRenderer({ block }: { block: NotionBlockSnapshot }) {
  const payload = getBlockPayload(block);

  switch (block.type) {
    case "paragraph":
      return (
        <div className="notion-block">
          <p>
            <RichText richText={getRichText(block)} />
          </p>
          {renderChildren(block)}
        </div>
      );
    case "heading_1":
      return renderHeading(block, 1);
    case "heading_2":
      return renderHeading(block, 2);
    case "heading_3":
      return renderHeading(block, 3);
    case "bulleted_list_item":
    case "numbered_list_item":
    case "to_do":
      return renderListBlock(block);
    case "toggle":
      return (
        <details className="notion-block notion-toggle">
          <summary>
            <RichText richText={getRichText(block)} />
          </summary>
          {renderChildren(block)}
        </details>
      );
    case "quote":
      return (
        <blockquote className="notion-block notion-quote">
          <RichText richText={getRichText(block)} />
          {renderChildren(block)}
        </blockquote>
      );
    case "callout":
      return (
        <div className="notion-block notion-callout">
          <div aria-hidden="true">{renderCalloutIcon(payload.icon)}</div>
          <div>
            <RichText richText={getRichText(block)} />
            {renderChildren(block)}
          </div>
        </div>
      );
    case "divider":
      return <hr className="notion-block notion-divider" />;
    case "code":
      return (
        <pre className="notion-block notion-code">
          <code>
            <RichText richText={getRichText(block)} />
          </code>
        </pre>
      );
    case "image":
      return renderMedia(block, "notion-image", "Image");
    case "video":
      return renderMedia(block, "notion-video", "Video");
    case "file":
      return renderMedia(block, "notion-file", "File");
    case "bookmark":
      return (
        <div className="notion-block notion-bookmark">
          {typeof payload.url === "string" ? (
            <a href={rewriteNotionHref(payload.url)}>{payload.url}</a>
          ) : null}
        </div>
      );
    case "table":
      return renderTable(block);
    case "column_list":
      return <div className="notion-block notion-column-list">{renderNotionBlocks(block.children)}</div>;
    case "column":
      return <div className="notion-column">{renderNotionBlocks(block.children)}</div>;
    case "child_page":
      return (
        <div className="notion-block notion-child-page">
          <strong>{typeof payload.title === "string" ? payload.title : "Untitled page"}</strong>
        </div>
      );
    default:
      return <div className="notion-unsupported">Unsupported Notion block: {block.type}</div>;
  }
}

export function renderNotionBlocks(blocks: NotionBlockSnapshot[]): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];

  for (let index = 0; index < blocks.length; index += 1) {
    const block = blocks[index];

    if (block.type === "bulleted_list_item" || block.type === "numbered_list_item" || block.type === "to_do") {
      const groupType = block.type;
      const items: NotionBlockSnapshot[] = [];

      while (index < blocks.length && blocks[index].type === groupType) {
        items.push(blocks[index]);
        index += 1;
      }

      index -= 1;

      const listTag =
        groupType === "numbered_list_item" ? "ol" : "ul";
      const ListTag = listTag;

      nodes.push(
        <ListTag key={`list-${items[0].id}`} className="notion-block notion-list">
          {items.map((item) => (
            <li key={item.id}>
              <NotionBlockRenderer block={item} />
            </li>
          ))}
        </ListTag>
      );
      continue;
    }

    nodes.push(<NotionBlockRenderer key={block.id} block={block} />);
  }

  return nodes;
}
