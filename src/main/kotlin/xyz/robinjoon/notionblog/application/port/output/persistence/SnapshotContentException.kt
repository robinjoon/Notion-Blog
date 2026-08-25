package xyz.robinjoon.notionblog.application.port.output.persistence

class SnapshotContentException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
