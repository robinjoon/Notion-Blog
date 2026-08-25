package xyz.robinjoon.notionblog.adapter.output.notion.dto.block

import xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext.NotionRichTextEnvelope

data class NotionTextBlockData(
    val richText: List<NotionRichTextEnvelope> = emptyList(),
    val color: String? = null,
    val isToggleable: Boolean = false,
    val checked: Boolean = false,
    val language: String? = null,
    val caption: List<NotionRichTextEnvelope> = emptyList(),
    val expression: String? = null,
)
