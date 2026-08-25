package xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext

data class NotionAnnotationsResponse(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
    val color: String = "default",
)
