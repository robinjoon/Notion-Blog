package xyz.robinjoon.notionblog.adapter.output.notion.dto.block

import xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext.NotionRichTextEnvelope

data class NotionMediaBlockData(
    val type: String? = null,
    val file: NotionHostedFileResponse? = null,
    val external: NotionExternalFileResponse? = null,
    val name: String? = null,
    val caption: List<NotionRichTextEnvelope> = emptyList(),
    val url: String? = null,
)

data class NotionHostedFileResponse(
    val url: String,
    val expiryTime: String? = null,
)

data class NotionExternalFileResponse(
    val url: String,
)
