package xyz.robinjoon.notionblog.domain.model

sealed interface NotionBlock {
    val id: String
    val children: List<NotionBlock>
}

data class RichText(
    val plainText: String,
    val annotations: RichTextAnnotations = RichTextAnnotations(),
    val link: String? = null,
)

data class RichTextAnnotations(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
    val color: RichTextColor = RichTextColor.DEFAULT,
)

enum class RichTextColor {
    DEFAULT,
    GRAY,
    BROWN,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
    PINK,
    RED,
}

data class ParagraphBlock(
    override val id: String,
    val richText: List<RichText>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class HeadingBlock(
    override val id: String,
    val level: HeadingLevel,
    val richText: List<RichText>,
    val isToggleable: Boolean = false,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

enum class HeadingLevel {
    ONE,
    TWO,
    THREE,
}

data class BulletedListItemBlock(
    override val id: String,
    val richText: List<RichText>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class NumberedListItemBlock(
    override val id: String,
    val richText: List<RichText>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class ToDoBlock(
    override val id: String,
    val richText: List<RichText>,
    val checked: Boolean,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class ToggleBlock(
    override val id: String,
    val richText: List<RichText>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class QuoteBlock(
    override val id: String,
    val richText: List<RichText>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class CalloutBlock(
    override val id: String,
    val richText: List<RichText>,
    val icon: String? = null,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class DividerBlock(
    override val id: String,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class CodeBlock(
    override val id: String,
    val richText: List<RichText>,
    val language: String,
    val caption: List<RichText> = emptyList(),
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class ImageBlock(
    override val id: String,
    val url: String,
    val caption: List<RichText> = emptyList(),
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class VideoBlock(
    override val id: String,
    val url: String,
    val caption: List<RichText> = emptyList(),
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class FileBlock(
    override val id: String,
    val url: String,
    val name: String,
    val caption: List<RichText> = emptyList(),
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class BookmarkBlock(
    override val id: String,
    val url: String,
    val caption: List<RichText> = emptyList(),
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class TableBlock(
    override val id: String,
    val width: Int,
    val hasColumnHeader: Boolean = false,
    val hasRowHeader: Boolean = false,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock {
    init {
        require(width > 0) { "table width must be positive" }
    }
}

data class TableRowBlock(
    override val id: String,
    val cells: List<List<RichText>>,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class ColumnBlock(
    override val id: String,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class ChildPageBlock(
    override val id: String,
    val title: String,
    val pageId: NotionPageId,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock

data class UnsupportedBlock(
    override val id: String,
    val type: String,
    override val children: List<NotionBlock> = emptyList(),
) : NotionBlock {
    init {
        require(type.isNotBlank()) { "unsupported block type must not be blank" }
    }
}
