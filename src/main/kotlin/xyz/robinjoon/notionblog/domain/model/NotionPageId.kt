package xyz.robinjoon.notionblog.domain.model

@JvmInline
value class NotionPageId(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{32}"))) { "Notion page id must be 32 lowercase hexadecimal characters" }
    }
}
