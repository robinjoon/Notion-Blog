package xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext

data class NotionRichTextEnvelope(
    val type: String,
    val text: NotionTextResponse? = null,
    val equation: NotionEquationResponse? = null,
    val mention: NotionMentionResponse? = null,
    val annotations: NotionAnnotationsResponse = NotionAnnotationsResponse(),
    val plainText: String,
    val href: String? = null,
)

data class NotionTextResponse(
    val content: String,
    val link: NotionUrlResponse? = null,
)

data class NotionEquationResponse(
    val expression: String,
)

data class NotionMentionResponse(
    val type: String,
    val page: NotionIdResponse? = null,
    val database: NotionIdResponse? = null,
    val user: NotionIdResponse? = null,
    val linkPreview: NotionUrlResponse? = null,
)

data class NotionIdResponse(
    val id: String,
)

data class NotionUrlResponse(
    val url: String,
)
