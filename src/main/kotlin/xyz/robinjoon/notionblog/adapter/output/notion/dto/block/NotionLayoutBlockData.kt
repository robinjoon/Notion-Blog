package xyz.robinjoon.notionblog.adapter.output.notion.dto.block

import xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext.NotionRichTextEnvelope

data class NotionLayoutBlockData(
    val widthRatio: Double? = null,
    val tableWidth: Int? = null,
    val hasColumnHeader: Boolean = false,
    val hasRowHeader: Boolean = false,
    val cells: List<List<NotionRichTextEnvelope>> = emptyList(),
)
