package xyz.robinjoon.notionblog.adapter.output.notion.dto

import tools.jackson.databind.JsonNode

internal data class NotionPageResponse(
    val id: String,
    val parent: NotionPageParentResponse,
    val url: String,
    val publicUrl: String?,
    val inTrash: Boolean,
    val lastEditedTime: String,
    val properties: JsonNode,
    val icon: JsonNode? = null,
    val cover: JsonNode? = null,
)

internal data class NotionPageParentResponse(
    val type: String,
    val pageId: String?,
    val dataSourceId: String? = null,
)
