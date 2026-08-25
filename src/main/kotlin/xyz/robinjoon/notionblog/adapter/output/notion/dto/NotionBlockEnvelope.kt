package xyz.robinjoon.notionblog.adapter.output.notion.dto

import tools.jackson.databind.JsonNode

internal data class NotionBlockEnvelope(
    val id: String,
    val type: String,
    val hasChildren: Boolean,
    val inTrash: Boolean,
    val payload: JsonNode,
)
