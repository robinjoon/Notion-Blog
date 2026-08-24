package xyz.robinjoon.notionblog.adapter.out.rendering

import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import xyz.robinjoon.notionblog.domain.model.BookmarkBlock
import xyz.robinjoon.notionblog.domain.model.BulletedListItemBlock
import xyz.robinjoon.notionblog.domain.model.CalloutBlock
import xyz.robinjoon.notionblog.domain.model.ChildPageBlock
import xyz.robinjoon.notionblog.domain.model.CodeBlock
import xyz.robinjoon.notionblog.domain.model.ColumnBlock
import xyz.robinjoon.notionblog.domain.model.DividerBlock
import xyz.robinjoon.notionblog.domain.model.FileBlock
import xyz.robinjoon.notionblog.domain.model.HeadingBlock
import xyz.robinjoon.notionblog.domain.model.HeadingLevel
import xyz.robinjoon.notionblog.domain.model.ImageBlock
import xyz.robinjoon.notionblog.domain.model.NotionBlock
import xyz.robinjoon.notionblog.domain.model.NotionPageReference
import xyz.robinjoon.notionblog.domain.model.NumberedListItemBlock
import xyz.robinjoon.notionblog.domain.model.ParagraphBlock
import xyz.robinjoon.notionblog.domain.model.QuoteBlock
import xyz.robinjoon.notionblog.domain.model.RichText
import xyz.robinjoon.notionblog.domain.model.TableBlock
import xyz.robinjoon.notionblog.domain.model.TableRowBlock
import xyz.robinjoon.notionblog.domain.model.ToDoBlock
import xyz.robinjoon.notionblog.domain.model.ToggleBlock
import xyz.robinjoon.notionblog.domain.model.UnsupportedBlock
import xyz.robinjoon.notionblog.domain.model.VideoBlock
import java.net.URI

@Component
class NotionPageRenderer(
    private val templateEngine: TemplateEngine,
) {
    fun render(title: String, blocks: List<NotionBlock>): String {
        val context = Context().apply {
            setVariable("title", title)
            setVariable("blocks", viewBlocks(blocks))
        }
        return templateEngine.process("notion/page", context)
    }

    private fun viewBlocks(blocks: List<NotionBlock>): List<BlockView> {
        val result = mutableListOf<BlockView>()
        var index = 0

        while (index < blocks.size) {
            val block = blocks[index]
            val listKind = listKind(block)
            if (listKind != null) {
                val items = mutableListOf<NotionBlock>()
                while (index < blocks.size && listKind(blocks[index]) == listKind) {
                    items += blocks[index]
                    index += 1
                }
                result += BlockView(
                    kind = "list",
                    id = items.first().id,
                    listKind = listKind,
                    items = items.map(::viewBlock),
                )
                continue
            }

            result += viewBlock(block)
            index += 1
        }
        return result
    }

    private fun listKind(block: NotionBlock): String? = when (block) {
        is BulletedListItemBlock -> "bulleted"
        is NumberedListItemBlock -> "numbered"
        is ToDoBlock -> "todo"
        else -> null
    }

    private fun viewBlock(block: NotionBlock): BlockView = when (block) {
        is ParagraphBlock -> BlockView("paragraph", block.id, richText = richText(block.richText), children = viewBlocks(block.children))

        is HeadingBlock -> BlockView(
            kind = headingKind(block.level),
            id = block.id,
            richText = richText(block.richText),
            children = viewBlocks(block.children),
        )

        is BulletedListItemBlock -> BlockView("bulleted_list_item", block.id, richText(block.richText), viewBlocks(block.children))

        is NumberedListItemBlock -> BlockView("numbered_list_item", block.id, richText(block.richText), viewBlocks(block.children))

        is ToDoBlock -> BlockView("to_do", block.id, richText(block.richText), viewBlocks(block.children), checked = block.checked)

        is ToggleBlock -> BlockView("toggle", block.id, richText(block.richText), viewBlocks(block.children))

        is QuoteBlock -> BlockView("quote", block.id, richText(block.richText), viewBlocks(block.children))

        is CalloutBlock -> BlockView(
            kind = "callout",
            id = block.id,
            richText = richText(block.richText),
            children = viewBlocks(block.children),
            icon = block.icon ?: "!",
        )

        is DividerBlock -> BlockView("divider", block.id)

        is CodeBlock -> BlockView(
            kind = "code",
            id = block.id,
            richText = richText(block.richText),
            caption = richText(block.caption),
            language = safeCssToken(block.language),
            children = viewBlocks(block.children),
        )

        is ImageBlock -> BlockView(
            kind = "image",
            id = block.id,
            url = safeMediaUrl(block.url),
            caption = richText(block.caption),
            children = viewBlocks(block.children),
        )

        is VideoBlock -> BlockView(
            kind = "video",
            id = block.id,
            url = safeMediaUrl(block.url),
            caption = richText(block.caption),
            children = viewBlocks(block.children),
        )

        is FileBlock -> BlockView(
            kind = "file",
            id = block.id,
            url = safeMediaUrl(block.url),
            fileName = block.name.ifBlank { "File" },
            caption = richText(block.caption),
            children = viewBlocks(block.children),
        )

        is BookmarkBlock -> BlockView(
            kind = "bookmark",
            id = block.id,
            url = safeHref(block.url),
            caption = richText(block.caption),
            children = viewBlocks(block.children),
        )

        is TableBlock -> BlockView(
            kind = "table",
            id = block.id,
            rows = block.children.filterIsInstance<TableRowBlock>().map(::viewRow),
        )

        is TableRowBlock -> BlockView(
            kind = "table_row",
            id = block.id,
            rows = listOf(viewRow(block)),
            children = viewBlocks(block.children),
        )

        is ColumnBlock -> BlockView("column", block.id, children = viewBlocks(block.children))

        is ChildPageBlock -> BlockView(
            kind = "child_page",
            id = block.id,
            title = block.title.ifBlank { "Untitled page" },
            url = "/notion/${block.pageId.value}",
            children = viewBlocks(block.children),
        )

        is UnsupportedBlock -> BlockView(
            kind = "unsupported",
            id = block.id,
            unsupportedType = block.type,
            children = viewBlocks(block.children),
        )
    }

    private fun viewRow(row: TableRowBlock): TableRowView = TableRowView(
        cells = row.cells.map(::richText),
    )

    private fun richText(tokens: List<RichText>): List<RichTextView> = tokens.map { token ->
        RichTextView(
            markup = richTextMarkup(token),
            href = safeHref(token.link),
        )
    }

    private fun richTextMarkup(token: RichText): String {
        var markup = HtmlUtils.htmlEscape(token.plainText)
        if (token.annotations.code) markup = "<code>$markup</code>"
        if (token.annotations.bold) markup = "<strong>$markup</strong>"
        if (token.annotations.italic) markup = "<em>$markup</em>"
        if (token.annotations.underline) markup = "<u>$markup</u>"
        if (token.annotations.strikethrough) markup = "<s>$markup</s>"
        return markup
    }

    private fun safeHref(value: String?): String? {
        val input = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        NotionPageReference.parse(input)?.let { return "/notion/${it.value}" }

        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        return input.takeIf { uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) }
    }

    private fun safeMediaUrl(value: String): String? {
        val input = value.trim().takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        return input.takeIf { uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) }
    }

    private fun safeCssToken(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9_-]"), "-")
        .trim('-')
        .ifBlank { "plain-text" }

    private fun headingKind(level: HeadingLevel): String = when (level) {
        HeadingLevel.ONE -> "heading_1"
        HeadingLevel.TWO -> "heading_2"
        HeadingLevel.THREE -> "heading_3"
    }
}

data class RichTextView(
    val markup: String,
    val href: String?,
)

data class TableRowView(
    val cells: List<List<RichTextView>>,
)

data class BlockView(
    val kind: String,
    val id: String,
    val richText: List<RichTextView> = emptyList(),
    val children: List<BlockView> = emptyList(),
    val listKind: String? = null,
    val items: List<BlockView> = emptyList(),
    val checked: Boolean = false,
    val icon: String? = null,
    val url: String? = null,
    val caption: List<RichTextView> = emptyList(),
    val language: String? = null,
    val fileName: String? = null,
    val rows: List<TableRowView> = emptyList(),
    val title: String? = null,
    val unsupportedType: String? = null,
)
