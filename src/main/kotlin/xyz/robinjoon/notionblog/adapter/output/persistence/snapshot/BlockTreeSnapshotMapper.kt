package xyz.robinjoon.notionblog.adapter.output.persistence.snapshot

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import xyz.robinjoon.notionblog.domain.post.block.BlockId
import xyz.robinjoon.notionblog.domain.post.block.BlockNode
import xyz.robinjoon.notionblog.domain.post.block.BlockTree
import xyz.robinjoon.notionblog.domain.post.block.content.BlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.BlockIcon
import xyz.robinjoon.notionblog.domain.post.block.content.HeadingLevel
import xyz.robinjoon.notionblog.domain.post.block.content.LayoutBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.ListBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaType
import xyz.robinjoon.notionblog.domain.post.block.content.MeetingNotesStatus
import xyz.robinjoon.notionblog.domain.post.block.content.NumberedListFormat
import xyz.robinjoon.notionblog.domain.post.block.content.ReferenceBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.ReusableBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.SpecialBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.SynchronizedBlockOrigin
import xyz.robinjoon.notionblog.domain.post.block.content.TextBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.UnsupportedBlockContent
import xyz.robinjoon.notionblog.domain.post.block.inline.InlineContent
import xyz.robinjoon.notionblog.domain.post.block.inline.LinkTarget
import xyz.robinjoon.notionblog.domain.post.block.inline.MentionKind
import xyz.robinjoon.notionblog.domain.post.block.inline.TextAnnotations
import xyz.robinjoon.notionblog.domain.post.block.media.MediaSource
import xyz.robinjoon.notionblog.domain.post.block.style.Alignment
import xyz.robinjoon.notionblog.domain.post.block.style.BlockStyle
import xyz.robinjoon.notionblog.domain.post.block.style.ColorToken
import xyz.robinjoon.notionblog.domain.post.block.style.StyleVariant
import xyz.robinjoon.notionblog.domain.post.block.style.WidthToken
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef
import xyz.robinjoon.notionblog.domain.source.SourceId
import java.net.URI
import java.time.Instant

internal class BlockTreeSnapshotMapper {
    fun toJson(tree: BlockTree): ObjectNode = objectNode().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("kind", DOCUMENT_KIND)
        set("blocks", arrayNode().also { blocks -> tree.roots.forEach { blocks.add(toJson(it)) } })
    }

    fun fromJson(document: JsonNode): BlockTree {
        val root = document.requireObject("snapshot document")
        val schemaVersion = root.requiredInt("schemaVersion", "snapshot document")
        require(schemaVersion == SCHEMA_VERSION) { "unsupported block tree snapshot schema version: $schemaVersion" }
        require(root.requiredText("kind", "snapshot document") == DOCUMENT_KIND) { "invalid block tree snapshot kind" }
        return BlockTree(root.requiredArray("blocks", "snapshot document").toList().map(::fromJsonNode))
    }

    private fun toJson(node: BlockNode): ObjectNode = objectNode().apply {
        put("id", node.id.value)
        put("kind", kindOf(node.content))
        set("style", toJson(node.style))
        set("content", toJsonContent(node.content))
        set("children", arrayNode().also { children -> node.children.forEach { children.add(toJson(it)) } })
    }

    private fun fromJsonNode(node: JsonNode): BlockNode {
        val objectNode = node.requireObject("block")
        val kind = objectNode.requiredText("kind", "block")
        val children = objectNode.requiredArray("children", "block").toList().map(::fromJsonNode)
        return BlockNode(
            id = BlockId(objectNode.requiredText("id", "block")),
            content = if (kind.isKnownBlockKind()) {
                fromJsonContent(kind, objectNode.requiredObject("content", "$kind block"))
            } else {
                UnsupportedBlockContent(kind)
            },
            style = fromJsonStyle(objectNode.requiredObject("style", "$kind block")),
            children = children,
        )
    }

    private fun kindOf(content: BlockContent): String = when (content) {
        is TextBlockContent.Paragraph -> "paragraph"
        is TextBlockContent.Heading -> "heading"
        is TextBlockContent.Quote -> "quote"
        is TextBlockContent.Toggle -> "toggle"
        is TextBlockContent.Callout -> "callout"
        is TextBlockContent.Code -> "code"
        is TextBlockContent.Equation -> "equation"
        is ListBlockContent.BulletedItem -> "bulleted_list_item"
        is ListBlockContent.NumberedItem -> "numbered_list_item"
        is ListBlockContent.ToDoItem -> "to_do"
        LayoutBlockContent.Divider -> "divider"
        LayoutBlockContent.ColumnList -> "column_list"
        is LayoutBlockContent.Column -> "column"
        LayoutBlockContent.TabContainer -> "tab_container"
        is LayoutBlockContent.TabItem -> "tab_item"
        is LayoutBlockContent.Table -> "table"
        is LayoutBlockContent.TableRow -> "table_row"
        is ReferenceBlockContent.ChildPost -> "child_post"
        is ReferenceBlockContent.DocumentLink -> "document_link"
        is ReferenceBlockContent.DatabaseLink -> "database_link"
        is ReferenceBlockContent.Breadcrumb -> "breadcrumb"
        ReferenceBlockContent.TableOfContents -> "table_of_contents"
        is MediaBlockContent.Media -> "media"
        is MediaBlockContent.Bookmark -> "bookmark"
        is MediaBlockContent.LinkPreview -> "link_preview"
        is MediaBlockContent.Embed -> "embed"
        is ReusableBlockContent.Synchronized -> "synchronized"
        is ReusableBlockContent.Template -> "template"
        is SpecialBlockContent.MeetingNotes -> "meeting_notes"
        is UnsupportedBlockContent -> "unsupported"
    }

    private fun String.isKnownBlockKind(): Boolean = this in setOf(
        "paragraph",
        "heading",
        "quote",
        "toggle",
        "callout",
        "code",
        "equation",
        "bulleted_list_item",
        "numbered_list_item",
        "to_do",
        "divider",
        "column_list",
        "column",
        "tab_container",
        "tab_item",
        "table",
        "table_row",
        "child_post",
        "document_link",
        "database_link",
        "breadcrumb",
        "table_of_contents",
        "media",
        "bookmark",
        "link_preview",
        "embed",
        "synchronized",
        "template",
        "meeting_notes",
        "unsupported",
    )

    private fun toJsonContent(content: BlockContent): ObjectNode = objectNode().apply {
        when (content) {
            is TextBlockContent.Paragraph -> set("richText", toJsonInlineList(content.richText))

            is TextBlockContent.Heading -> {
                put("level", content.level.logicalValue())
                set("richText", toJsonInlineList(content.richText))
                put("isToggleable", content.isToggleable)
            }

            is TextBlockContent.Quote -> set("richText", toJsonInlineList(content.richText))

            is TextBlockContent.Toggle -> set("richText", toJsonInlineList(content.richText))

            is TextBlockContent.Callout -> {
                set("richText", toJsonInlineList(content.richText))
                set("icon", content.icon?.let(::toJsonIcon) ?: nullNode())
            }

            is TextBlockContent.Code -> {
                set("richText", toJsonInlineList(content.richText))
                put("language", content.language)
                set("caption", toJsonInlineList(content.caption))
            }

            is TextBlockContent.Equation -> put("expression", content.expression)

            is ListBlockContent.BulletedItem -> set("richText", toJsonInlineList(content.richText))

            is ListBlockContent.NumberedItem -> {
                set("richText", toJsonInlineList(content.richText))
                put("startNumber", content.startNumber)
                put("displayFormat", content.displayFormat.logicalValue())
                put("startsNewList", content.startsNewList)
            }

            is ListBlockContent.ToDoItem -> {
                set("richText", toJsonInlineList(content.richText))
                put("checked", content.checked)
            }

            LayoutBlockContent.Divider, LayoutBlockContent.ColumnList, LayoutBlockContent.TabContainer, ReferenceBlockContent.TableOfContents -> Unit

            is LayoutBlockContent.Column -> set("width", content.width?.let { numberNode(it.ratio) } ?: nullNode())

            is LayoutBlockContent.TabItem -> {
                set("title", toJsonInlineList(content.title))
                set("icon", content.icon?.let(::toJsonIcon) ?: nullNode())
            }

            is LayoutBlockContent.Table -> {
                put("width", content.width)
                put("hasColumnHeader", content.hasColumnHeader)
                put("hasRowHeader", content.hasRowHeader)
            }

            is LayoutBlockContent.TableRow -> set("cells", arrayNode().also { cells -> content.cells.forEach { cells.add(toJsonInlineList(it)) } })

            is ReferenceBlockContent.ChildPost -> {
                put("title", content.title)
                set("reference", toJsonReference(content.reference))
            }

            is ReferenceBlockContent.DocumentLink -> {
                set("reference", toJsonReference(content.reference))
                set("originalUrl", content.originalUrl?.let { jsonString(it.toString()) } ?: nullNode())
            }

            is ReferenceBlockContent.DatabaseLink -> {
                set("reference", toJsonReference(content.reference))
                set("originalUrl", content.originalUrl?.let { jsonString(it.toString()) } ?: nullNode())
                set("title", content.title?.let(::jsonString) ?: nullNode())
            }

            is ReferenceBlockContent.Breadcrumb -> set("items", arrayNode().also { items -> content.items.forEach { items.add(toJsonLink(it)) } })

            is MediaBlockContent.Media -> {
                put("mediaType", content.mediaType.logicalValue())
                set("source", toJsonMediaSource(content.source))
                set("fileName", content.fileName?.let(::jsonString) ?: nullNode())
                set("caption", toJsonInlineList(content.caption))
            }

            is MediaBlockContent.Bookmark -> {
                put("url", content.url.toString())
                set("caption", toJsonInlineList(content.caption))
            }

            is MediaBlockContent.LinkPreview -> put("url", content.url.toString())

            is MediaBlockContent.Embed -> {
                put("url", content.url.toString())
                set("caption", toJsonInlineList(content.caption))
            }

            is ReusableBlockContent.Synchronized -> set("origin", content.origin?.let(::toJsonOrigin) ?: nullNode())

            is ReusableBlockContent.Template -> set("title", toJsonInlineList(content.title))

            is SpecialBlockContent.MeetingNotes -> {
                put("title", content.title)
                put("status", content.status.logicalValue())
                set("summary", toJsonInlineList(content.summary))
                set("notesReference", content.notesReference?.let(::toJsonLink) ?: nullNode())
            }

            is UnsupportedBlockContent -> put("blockType", content.blockType)
        }
    }

    private fun fromJsonContent(kind: String, content: ObjectNode): BlockContent = when (kind) {
        "paragraph" -> TextBlockContent.Paragraph(content.requiredInlineList("richText", kind))

        "heading" -> TextBlockContent.Heading(content.requiredEnum("level", kind, ::headingLevel), content.requiredInlineList("richText", kind), content.requiredBoolean("isToggleable", kind))

        "quote" -> TextBlockContent.Quote(content.requiredInlineList("richText", kind))

        "toggle" -> TextBlockContent.Toggle(content.requiredInlineList("richText", kind))

        "callout" -> TextBlockContent.Callout(content.requiredInlineList("richText", kind), content.optionalObject("icon")?.let(::fromJsonIcon))

        "code" -> TextBlockContent.Code(content.requiredInlineList("richText", kind), content.requiredText("language", kind), content.requiredInlineList("caption", kind))

        "equation" -> TextBlockContent.Equation(content.requiredText("expression", kind))

        "bulleted_list_item" -> ListBlockContent.BulletedItem(content.requiredInlineList("richText", kind))

        "numbered_list_item" -> ListBlockContent.NumberedItem(
            richText = content.requiredInlineList("richText", kind),
            startNumber = content.requiredInt("startNumber", kind),
            displayFormat = content.requiredEnum("displayFormat", kind, ::numberedListFormat),
            startsNewList = content.optionalBoolean("startsNewList") ?: false,
        )

        "to_do" -> ListBlockContent.ToDoItem(content.requiredInlineList("richText", kind), content.requiredBoolean("checked", kind))

        "divider" -> LayoutBlockContent.Divider

        "column_list" -> LayoutBlockContent.ColumnList

        "column" -> LayoutBlockContent.Column(content.optionalDouble("width")?.let(::WidthToken))

        "tab_container" -> LayoutBlockContent.TabContainer

        "tab_item" -> LayoutBlockContent.TabItem(content.requiredInlineList("title", kind), content.optionalObject("icon")?.let(::fromJsonIcon))

        "table" -> LayoutBlockContent.Table(content.requiredInt("width", kind), content.requiredBoolean("hasColumnHeader", kind), content.requiredBoolean("hasRowHeader", kind))

        "table_row" -> LayoutBlockContent.TableRow(content.requiredArray("cells", kind).toList().map { it.requireArray("$kind cell").toList().map(::fromJsonInline) })

        "child_post" -> ReferenceBlockContent.ChildPost(content.requiredText("title", kind), fromJsonReference(content.requiredObject("reference", kind)))

        "document_link" -> ReferenceBlockContent.DocumentLink(fromJsonReference(content.requiredObject("reference", kind)), content.optionalUri("originalUrl"))

        "database_link" -> ReferenceBlockContent.DatabaseLink(
            reference = fromJsonReference(content.requiredObject("reference", kind)),
            originalUrl = content.optionalUri("originalUrl"),
            title = content.optionalText("title"),
        )

        "breadcrumb" -> ReferenceBlockContent.Breadcrumb(content.requiredArray("items", kind).toList().map(::fromJsonLink))

        "table_of_contents" -> ReferenceBlockContent.TableOfContents

        "media" -> MediaBlockContent.Media(content.requiredEnum("mediaType", kind, ::mediaType), fromJsonMediaSource(content.requiredObject("source", kind)), content.optionalText("fileName"), content.requiredInlineList("caption", kind))

        "bookmark" -> MediaBlockContent.Bookmark(content.requiredUri("url", kind), content.requiredInlineList("caption", kind))

        "link_preview" -> MediaBlockContent.LinkPreview(content.requiredUri("url", kind))

        "embed" -> MediaBlockContent.Embed(content.requiredUri("url", kind), content.requiredInlineList("caption", kind))

        "synchronized" -> ReusableBlockContent.Synchronized(content.optionalObject("origin")?.let(::fromJsonOrigin))

        "template" -> ReusableBlockContent.Template(content.optionalInlineList("title"))

        "meeting_notes" -> SpecialBlockContent.MeetingNotes(content.requiredText("title", kind), content.requiredEnum("status", kind, ::meetingNotesStatus), content.requiredInlineList("summary", kind), content.optionalObject("notesReference")?.let(::fromJsonLink))

        "unsupported" -> UnsupportedBlockContent(content.requiredText("blockType", kind))

        else -> UnsupportedBlockContent(kind)
    }

    private fun toJson(style: BlockStyle): ObjectNode = objectNode().apply {
        set("foreground", style.foreground?.let { jsonString(it.logicalValue()) } ?: nullNode())
        set("background", style.background?.let { jsonString(it.logicalValue()) } ?: nullNode())
        set("alignment", style.alignment?.let { jsonString(it.logicalValue()) } ?: nullNode())
        set("width", style.width?.let { numberNode(it.ratio) } ?: nullNode())
        set("variant", style.variant?.let { jsonString(it.value) } ?: nullNode())
    }

    private fun fromJsonStyle(style: ObjectNode): BlockStyle = BlockStyle(
        foreground = style.optionalEnum("foreground", ::colorToken),
        background = style.optionalEnum("background", ::colorToken),
        alignment = style.optionalEnum("alignment", ::alignment),
        width = style.optionalDouble("width")?.let(::WidthToken),
        variant = style.optionalText("variant")?.let(::StyleVariant),
    )

    private fun toJsonInlineList(inline: List<InlineContent>): ArrayNode = arrayNode().also { values -> inline.forEach { values.add(toJsonInline(it)) } }

    private fun toJsonInline(inline: InlineContent): ObjectNode = objectNode().apply {
        set("annotations", toJson(inline.annotations))
        when (inline) {
            is InlineContent.Text -> {
                put("kind", "text")
                put("text", inline.text)
                set("link", inline.link?.let(::toJsonLink) ?: nullNode())
            }

            is InlineContent.Equation -> {
                put("kind", "equation")
                put("expression", inline.expression)
            }

            is InlineContent.Mention -> {
                put("kind", "mention")
                put("label", inline.label)
                put("mentionKind", inline.kind.logicalValue())
                set("target", inline.target?.let(::toJsonLink) ?: nullNode())
            }
        }
    }

    private fun fromJsonInline(node: JsonNode): InlineContent {
        val inline = node.requireObject("inline content")
        val annotations = fromJsonAnnotations(inline.requiredObject("annotations", "inline content"))
        return when (val kind = inline.requiredText("kind", "inline content")) {
            "text" -> InlineContent.Text(inline.requiredText("text", kind), annotations, inline.optionalObject("link")?.let(::fromJsonLink))
            "equation" -> InlineContent.Equation(inline.requiredText("expression", kind), annotations)
            "mention" -> InlineContent.Mention(inline.requiredText("label", kind), inline.requiredEnum("mentionKind", kind, ::mentionKind), annotations, inline.optionalObject("target")?.let(::fromJsonLink))
            else -> throw IllegalArgumentException("unsupported inline content kind: $kind")
        }
    }

    private fun toJson(annotations: TextAnnotations): ObjectNode = objectNode().apply {
        put("bold", annotations.bold)
        put("italic", annotations.italic)
        put("strikethrough", annotations.strikethrough)
        put("underline", annotations.underline)
        put("code", annotations.code)
        set("foreground", annotations.foreground?.let { jsonString(it.logicalValue()) } ?: nullNode())
        set("background", annotations.background?.let { jsonString(it.logicalValue()) } ?: nullNode())
    }

    private fun fromJsonAnnotations(annotations: ObjectNode): TextAnnotations = TextAnnotations(
        bold = annotations.requiredBoolean("bold", "annotations"),
        italic = annotations.requiredBoolean("italic", "annotations"),
        strikethrough = annotations.requiredBoolean("strikethrough", "annotations"),
        underline = annotations.requiredBoolean("underline", "annotations"),
        code = annotations.requiredBoolean("code", "annotations"),
        foreground = annotations.optionalEnum("foreground", ::colorToken),
        background = annotations.optionalEnum("background", ::colorToken),
    )

    private fun toJsonLink(link: LinkTarget): ObjectNode = objectNode().apply {
        when (link) {
            is LinkTarget.ExternalUrl -> {
                put("kind", "external_url")
                put("url", link.url.toString())
            }

            is LinkTarget.SourceDocument -> {
                put("kind", "source_document")
                set("reference", toJsonReference(link.reference))
                set("originalUrl", link.originalUrl?.let { jsonString(it.toString()) } ?: nullNode())
            }
        }
    }

    private fun fromJsonLink(node: JsonNode): LinkTarget {
        val link = node.requireObject("link")
        return when (val kind = link.requiredText("kind", "link")) {
            "external_url" -> LinkTarget.ExternalUrl(link.requiredUri("url", kind))
            "source_document" -> LinkTarget.SourceDocument(fromJsonReference(link.requiredObject("reference", kind)), link.optionalUri("originalUrl"))
            else -> throw IllegalArgumentException("unsupported link kind: $kind")
        }
    }

    private fun toJsonReference(reference: SourceDocumentRef): ObjectNode = objectNode().apply {
        put("sourceId", reference.sourceId.value)
        put("externalId", reference.externalId)
    }

    private fun fromJsonReference(reference: ObjectNode): SourceDocumentRef = SourceDocumentRef(
        SourceId(reference.requiredText("sourceId", "source document reference")),
        reference.requiredText("externalId", "source document reference"),
    )

    private fun toJsonMediaSource(source: MediaSource): ObjectNode = objectNode().apply {
        when (source) {
            is MediaSource.External -> {
                put("kind", "external")
                put("url", source.url.toString())
            }

            is MediaSource.SourceHosted -> {
                put("kind", "source_hosted")
                put("url", source.url.toString())
                set("expiresAt", source.expiresAt?.let { jsonString(it.toString()) } ?: nullNode())
            }
        }
    }

    private fun fromJsonMediaSource(node: ObjectNode): MediaSource = when (val kind = node.requiredText("kind", "media source")) {
        "external" -> MediaSource.External(node.requiredUri("url", kind))
        "source_hosted" -> MediaSource.SourceHosted(node.requiredUri("url", kind), node.optionalInstant("expiresAt"))
        else -> throw IllegalArgumentException("unsupported media source kind: $kind")
    }

    private fun toJsonIcon(icon: BlockIcon): ObjectNode = objectNode().apply {
        when (icon) {
            is BlockIcon.Emoji -> {
                put("kind", "emoji")
                put("value", icon.value)
            }

            is BlockIcon.Media -> {
                put("kind", "media")
                set("source", toJsonMediaSource(icon.source))
            }

            is BlockIcon.Native -> {
                put("kind", "native")
                put("name", icon.name)
                set("color", icon.color?.let { jsonString(it.logicalValue()) } ?: nullNode())
            }

            is BlockIcon.CustomEmoji -> {
                put("kind", "custom_emoji")
                put("externalId", icon.externalId)
                put("name", icon.name)
                set("source", toJsonMediaSource(icon.source))
            }
        }
    }

    private fun fromJsonIcon(node: ObjectNode): BlockIcon = when (val kind = node.requiredText("kind", "block icon")) {
        "emoji" -> BlockIcon.Emoji(node.requiredText("value", kind))

        "media" -> BlockIcon.Media(fromJsonMediaSource(node.requiredObject("source", kind)))

        "native" -> BlockIcon.Native(node.requiredText("name", kind), node.optionalEnum("color", ::colorToken))

        "custom_emoji" -> BlockIcon.CustomEmoji(
            externalId = node.requiredText("externalId", kind),
            name = node.requiredText("name", kind),
            source = fromJsonExternalMediaSource(node.requiredObject("source", kind)),
        )

        else -> throw IllegalArgumentException("unsupported block icon kind: $kind")
    }

    private fun fromJsonExternalMediaSource(node: ObjectNode): MediaSource.External {
        val source = fromJsonMediaSource(node)
        require(source is MediaSource.External) { "custom emoji source must be external" }
        return source
    }

    private fun toJsonOrigin(origin: SynchronizedBlockOrigin): ObjectNode = objectNode().apply {
        set("document", toJsonReference(origin.document))
        put("blockExternalId", origin.blockExternalId)
    }

    private fun fromJsonOrigin(node: ObjectNode): SynchronizedBlockOrigin = SynchronizedBlockOrigin(
        fromJsonReference(node.requiredObject("document", "synchronized block origin")),
        node.requiredText("blockExternalId", "synchronized block origin"),
    )

    private fun JsonNode.requireObject(context: String): ObjectNode = this as? ObjectNode
        ?: throw IllegalArgumentException("$context must be an object")

    private fun ObjectNode.requiredObject(name: String, context: String): ObjectNode = requiredNode(name, context).requireObject("$context.$name")

    private fun ObjectNode.optionalObject(name: String): ObjectNode? = get(name)?.takeUnless(JsonNode::isNull)?.requireObject(name)

    private fun ObjectNode.requiredArray(name: String, context: String): ArrayNode = requiredNode(name, context) as? ArrayNode
        ?: throw IllegalArgumentException("$context.$name must be an array")

    private fun JsonNode.requireArray(context: String): ArrayNode = this as? ArrayNode
        ?: throw IllegalArgumentException("$context must be an array")

    private fun ObjectNode.requiredText(name: String, context: String): String {
        val value = requiredNode(name, context)
        require(value.isString) { "$context.$name must be a string" }
        return value.asString()
    }

    private fun ObjectNode.optionalText(name: String): String? = get(name)?.takeUnless(JsonNode::isNull)?.let { value ->
        require(value.isString) { "$name must be a string" }
        value.asString()
    }

    private fun ObjectNode.requiredBoolean(name: String, context: String): Boolean {
        val value = requiredNode(name, context)
        require(value.isBoolean) { "$context.$name must be a boolean" }
        return value.asBoolean()
    }

    private fun ObjectNode.optionalBoolean(name: String): Boolean? {
        val value = get(name) ?: return null
        require(value.isBoolean) { "$name must be a boolean" }
        return value.asBoolean()
    }

    private fun ObjectNode.requiredInt(name: String, context: String): Int {
        val value = requiredNode(name, context)
        require(value.canConvertToInt()) { "$context.$name must be an integer" }
        return value.asInt()
    }

    private fun ObjectNode.optionalDouble(name: String): Double? = get(name)?.takeUnless(JsonNode::isNull)?.let { value ->
        require(value.isNumber) { "$name must be a number" }
        value.asDouble()
    }

    private fun ObjectNode.requiredUri(name: String, context: String): URI = uri(requiredText(name, context), "$context.$name")

    private fun ObjectNode.optionalUri(name: String): URI? = optionalText(name)?.let { uri(it, name) }

    private fun ObjectNode.optionalInstant(name: String): Instant? = optionalText(name)?.let {
        try {
            Instant.parse(it)
        } catch (exception: Exception) {
            throw IllegalArgumentException("$name must be an instant", exception)
        }
    }

    private fun <T> ObjectNode.requiredEnum(name: String, context: String, parser: (String) -> T): T = parser(requiredText(name, context))

    private fun <T> ObjectNode.optionalEnum(name: String, parser: (String) -> T): T? = optionalText(name)?.let(parser)

    private fun ObjectNode.requiredInlineList(name: String, context: String): List<InlineContent> = requiredArray(name, context).toList().map(::fromJsonInline)

    private fun ObjectNode.optionalInlineList(name: String): List<InlineContent> = get(name)
        ?.requireArray(name)
        ?.toList()
        ?.map(::fromJsonInline)
        .orEmpty()

    private fun ObjectNode.requiredNode(name: String, context: String): JsonNode = get(name)
        ?: throw IllegalArgumentException("$context.$name is required")

    private fun uri(value: String, context: String): URI = try {
        URI(value)
    } catch (exception: Exception) {
        throw IllegalArgumentException("$context must be a URI", exception)
    }

    private fun colorToken(value: String): ColorToken = enumValue(value, "color token") { ColorToken.valueOf(it) }

    private fun alignment(value: String): Alignment = enumValue(value, "alignment") { Alignment.valueOf(it) }

    private fun headingLevel(value: String): HeadingLevel = enumValue(value, "heading level") { HeadingLevel.valueOf(it) }

    private fun numberedListFormat(value: String): NumberedListFormat = enumValue(value, "numbered list format") { NumberedListFormat.valueOf(it) }

    private fun mediaType(value: String): MediaType = enumValue(value, "media type") { MediaType.valueOf(it) }

    private fun meetingNotesStatus(value: String): MeetingNotesStatus = enumValue(value, "meeting notes status") { MeetingNotesStatus.valueOf(it) }

    private fun mentionKind(value: String): MentionKind = enumValue(value, "mention kind") { MentionKind.valueOf(it) }

    private fun <T> enumValue(value: String, label: String, resolver: (String) -> T): T = try {
        resolver(value.uppercase())
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException("unsupported $label: $value", exception)
    }

    private fun Enum<*>.logicalValue(): String = name.lowercase()

    private fun objectNode(): ObjectNode = JsonNodeFactory.instance.objectNode()

    private fun arrayNode(): ArrayNode = JsonNodeFactory.instance.arrayNode()

    private fun nullNode(): JsonNode = JsonNodeFactory.instance.nullNode()

    private fun jsonString(value: String): JsonNode = JsonNodeFactory.instance.stringNode(value)

    private fun numberNode(value: Double): JsonNode = JsonNodeFactory.instance.numberNode(value)

    private companion object {
        const val SCHEMA_VERSION = 1
        const val DOCUMENT_KIND = "block_tree_snapshot"
    }
}
