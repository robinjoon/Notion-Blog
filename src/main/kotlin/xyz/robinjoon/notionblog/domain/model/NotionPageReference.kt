package xyz.robinjoon.notionblog.domain.model

import java.net.URI

object NotionPageReference {
    private val pageIdPattern = Regex(
        "(?<![0-9a-f])([0-9a-f]{32}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})(?![0-9a-f])",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): NotionPageId? {
        val trimmed = input.trim()
        normalizeId(trimmed)?.let { return it }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!isNotionHost(uri.host)) {
            return null
        }

        return pageIdPattern.findAll(uri.path.orEmpty()).lastOrNull()?.value?.let(::normalizeId)
    }

    private fun isNotionHost(host: String?): Boolean {
        val normalizedHost = host?.lowercase() ?: return false
        return normalizedHost == "notion.com" || normalizedHost.endsWith(".notion.com") ||
            normalizedHost == "notion.so" || normalizedHost.endsWith(".notion.so") ||
            normalizedHost == "notion.site" || normalizedHost.endsWith(".notion.site")
    }

    private fun normalizeId(value: String): NotionPageId? {
        val normalized = value.replace("-", "").lowercase()
        return normalized.takeIf { it.matches(Regex("[0-9a-f]{32}")) }?.let(::NotionPageId)
    }
}
