package xyz.robinjoon.notionblog.adapter.output.notion.mapping

import xyz.robinjoon.notionblog.application.port.output.source.SourceMappingException

internal object NotionIdNormalizer {
    fun normalize(value: String): String {
        val trimmed = value.trim()
        val compact = when {
            UNDASHED_PAGE_ID.matches(trimmed) -> trimmed
            DASHED_PAGE_ID.matches(trimmed) -> trimmed.replace("-", "")
            else -> throw SourceMappingException("Notion page ID is malformed")
        }
        return compact.lowercase()
    }

    private val UNDASHED_PAGE_ID = Regex("[0-9a-fA-F]{32}")
    private val DASHED_PAGE_ID = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
    )
}
