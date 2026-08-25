package xyz.robinjoon.notionblog.domain.sync

enum class SyncFailureKind {
    RETRYABLE_SOURCE,
    AUTHENTICATION,
    ACCESS,
    CONFIGURATION,
    MAPPING,
}
