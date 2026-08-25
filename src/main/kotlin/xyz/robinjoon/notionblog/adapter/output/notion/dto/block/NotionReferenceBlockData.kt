package xyz.robinjoon.notionblog.adapter.output.notion.dto.block

import xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext.NotionRichTextEnvelope

data class NotionReferenceBlockData(
    val title: String? = null,
    val pageId: String? = null,
    val databaseId: String? = null,
    val syncedFrom: NotionSyncedFromResponse? = null,
    val blockType: String? = null,
    val richText: List<NotionRichTextEnvelope> = emptyList(),
)

data class NotionSyncedFromResponse(
    val blockId: String,
)
