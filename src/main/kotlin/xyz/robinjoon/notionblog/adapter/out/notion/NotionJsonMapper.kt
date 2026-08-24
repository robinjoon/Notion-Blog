package xyz.robinjoon.notionblog.adapter.out.notion

import tools.jackson.databind.JsonNode
import xyz.robinjoon.notionblog.application.port.out.notion.NotionConfigurationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionPageMetadata
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingKind
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingsRow
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
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NotionPageReference
import xyz.robinjoon.notionblog.domain.model.NumberedListItemBlock
import xyz.robinjoon.notionblog.domain.model.ParagraphBlock
import xyz.robinjoon.notionblog.domain.model.QuoteBlock
import xyz.robinjoon.notionblog.domain.model.RichText
import xyz.robinjoon.notionblog.domain.model.RichTextAnnotations
import xyz.robinjoon.notionblog.domain.model.RichTextColor
import xyz.robinjoon.notionblog.domain.model.TableBlock
import xyz.robinjoon.notionblog.domain.model.TableRowBlock
import xyz.robinjoon.notionblog.domain.model.ToDoBlock
import xyz.robinjoon.notionblog.domain.model.ToggleBlock
import xyz.robinjoon.notionblog.domain.model.UnsupportedBlock
import xyz.robinjoon.notionblog.domain.model.VideoBlock
import java.time.Instant
import java.util.Locale

internal class NotionJsonMapper {
    fun mapPageMetadata(page: JsonNode): NotionPageMetadata {
        val properties = page.path("properties")
        val title = properties.values()
            .firstOrNull { it.path("type").asString("") == "title" }
            ?.path("title")
            ?.joinToString(separator = "") { it.path("plain_text").asString("") }
            .orEmpty()
        return NotionPageMetadata(
            id = parsePageId(requiredText(page, "id")),
            title = title,
            notionUrl = requiredText(page, "url"),
            publicUrl = page.path("public_url").takeUnless { it.isMissingNode || it.isNull }?.asString(),
            lastEditedAt = Instant.parse(requiredText(page, "last_edited_time")),
        )
    }

    fun mapSettingsRow(row: JsonNode): NotionSettingsRow {
        val properties = row.path("properties")
        return NotionSettingsRow(
            key = plainText(properties.path("Key")),
            kind = when (plainText(properties.path("Kind")).lowercase(Locale.ROOT)) {
                "page" -> NotionSettingKind.PAGE
                "blocks" -> NotionSettingKind.BLOCKS
                "head" -> NotionSettingKind.HEAD
                else -> throw NotionConfigurationException("Notion settings row has an unsupported kind")
            },
            enabled = properties.path("Enabled").takeIf { it.path("type").asString("") == "checkbox" }
                ?.path("checkbox")?.asBoolean(false) ?: false,
            page = plainText(properties.path("Page")),
            data = plainText(properties.path("Data")),
        )
    }

    fun mapBlock(block: JsonNode, children: List<NotionBlock>): NotionBlock {
        val id = requiredText(block, "id")
        val type = requiredText(block, "type")
        val data = block.path(type)
        return when (type) {
            "paragraph" -> ParagraphBlock(id, richText(data), children)

            "heading_1" -> HeadingBlock(id, HeadingLevel.ONE, richText(data), data.path("is_toggleable").asBoolean(false), children)

            "heading_2" -> HeadingBlock(id, HeadingLevel.TWO, richText(data), data.path("is_toggleable").asBoolean(false), children)

            "heading_3" -> HeadingBlock(id, HeadingLevel.THREE, richText(data), data.path("is_toggleable").asBoolean(false), children)

            "bulleted_list_item" -> BulletedListItemBlock(id, richText(data), children)

            "numbered_list_item" -> NumberedListItemBlock(id, richText(data), children)

            "to_do" -> ToDoBlock(id, richText(data), data.path("checked").asBoolean(false), children)

            "toggle" -> ToggleBlock(id, richText(data), children)

            "quote" -> QuoteBlock(id, richText(data), children)

            "callout" -> CalloutBlock(id, richText(data), icon(data.path("icon")), children)

            "divider" -> DividerBlock(id, children)

            "code" -> CodeBlock(
                id = id,
                richText = richText(data),
                language = data.path("language").asString("plain text"),
                caption = richTextArray(data.path("caption")),
                children = children,
            )

            "image" -> ImageBlock(id, mediaUrl(data), richTextArray(data.path("caption")), children)

            "video" -> VideoBlock(id, mediaUrl(data), richTextArray(data.path("caption")), children)

            "file" -> FileBlock(
                id = id,
                url = mediaUrl(data),
                name = data.path("name").asString(""),
                caption = richTextArray(data.path("caption")),
                children = children,
            )

            "bookmark" -> BookmarkBlock(
                id = id,
                url = requiredText(data, "url"),
                caption = richTextArray(data.path("caption")),
                children = children,
            )

            "table" -> TableBlock(
                id = id,
                width = data.path("table_width").asInt(),
                hasColumnHeader = data.path("has_column_header").asBoolean(false),
                hasRowHeader = data.path("has_row_header").asBoolean(false),
                children = children,
            )

            "table_row" -> TableRowBlock(
                id = id,
                cells = data.path("cells").toList().map(::richTextArray),
                children = children,
            )

            "column", "column_list" -> ColumnBlock(id, children)

            "child_page" -> ChildPageBlock(
                id = id,
                title = data.path("title").asString(""),
                pageId = parsePageId(id),
                children = children,
            )

            else -> UnsupportedBlock(id, type, children)
        }
    }

    fun collectLinkedPageIds(node: JsonNode, target: LinkedHashSet<NotionPageId>, key: String? = null) {
        when {
            node.isString -> {
                if (key == "href" || key == "url") {
                    NotionPageReference.parse(node.asString())?.let(target::add)
                }
            }

            node.isArray -> node.forEach { collectLinkedPageIds(it, target) }

            node.isObject -> {
                if (node.path("type").asString("") == "child_page") {
                    NotionPageReference.parse(node.path("id").asString(""))?.let(target::add)
                }
                val linkToPage = node.path("link_to_page")
                if (node.path("type").asString("") == "link_to_page" && linkToPage.path("type").asString("") == "page_id") {
                    NotionPageReference.parse(linkToPage.path("page_id").asString(""))?.let(target::add)
                }
                val mention = node.path("mention")
                if (mention.path("type").asString("") == "page") {
                    NotionPageReference.parse(mention.path("page").path("id").asString(""))?.let(target::add)
                }
                node.properties().forEach { (childKey, child) -> collectLinkedPageIds(child, target, childKey) }
            }
        }
    }

    private fun richText(data: JsonNode): List<RichText> = richTextArray(data.path("rich_text"))

    private fun richTextArray(array: JsonNode): List<RichText> = array.toList().map { segment ->
        val annotations = segment.path("annotations")
        RichText(
            plainText = segment.path("plain_text").asString(""),
            annotations = RichTextAnnotations(
                bold = annotations.path("bold").asBoolean(false),
                italic = annotations.path("italic").asBoolean(false),
                strikethrough = annotations.path("strikethrough").asBoolean(false),
                underline = annotations.path("underline").asBoolean(false),
                code = annotations.path("code").asBoolean(false),
                color = richTextColor(annotations.path("color").asString("default")),
            ),
            link = segment.path("href").takeUnless { it.isMissingNode || it.isNull }?.asString()
                ?: segment.path("text").path("link").path("url").takeUnless { it.isMissingNode || it.isNull }?.asString(),
        )
    }

    private fun plainText(property: JsonNode): String = when (property.path("type").asString("")) {
        "title" -> property.path("title").joinToString(separator = "") { it.path("plain_text").asString("") }
        "rich_text" -> property.path("rich_text").joinToString(separator = "") { it.path("plain_text").asString("") }
        "url" -> property.path("url").asString("")
        "select" -> property.path("select").path("name").asString("")
        else -> ""
    }

    private fun mediaUrl(data: JsonNode): String {
        val sourceType = data.path("type").asString("")
        return data.path(sourceType).path("url").asString("").takeIf(String::isNotBlank)
            ?: data.path("external").path("url").asString("").takeIf(String::isNotBlank)
            ?: data.path("file").path("url").asString("").takeIf(String::isNotBlank)
            ?: throw NotionConfigurationException("Notion media block is missing its URL")
    }

    private fun icon(icon: JsonNode): String? = when (icon.path("type").asString("")) {
        "emoji" -> icon.path("emoji").asString("").takeIf(String::isNotBlank)
        "external" -> icon.path("external").path("url").asString("").takeIf(String::isNotBlank)
        "file" -> icon.path("file").path("url").asString("").takeIf(String::isNotBlank)
        else -> null
    }

    private fun richTextColor(value: String): RichTextColor = when (value.removeSuffix("_background").uppercase(Locale.ROOT)) {
        "GRAY" -> RichTextColor.GRAY
        "BROWN" -> RichTextColor.BROWN
        "ORANGE" -> RichTextColor.ORANGE
        "YELLOW" -> RichTextColor.YELLOW
        "GREEN" -> RichTextColor.GREEN
        "BLUE" -> RichTextColor.BLUE
        "PURPLE" -> RichTextColor.PURPLE
        "PINK" -> RichTextColor.PINK
        "RED" -> RichTextColor.RED
        else -> RichTextColor.DEFAULT
    }

    private fun parsePageId(value: String): NotionPageId = NotionPageReference.parse(value)
        ?: throw NotionConfigurationException("Notion response contains an invalid page ID")

    private fun requiredText(node: JsonNode, field: String): String = node.path(field).asString("").takeIf(String::isNotBlank)
        ?: throw NotionConfigurationException("Notion response is missing $field")
}
