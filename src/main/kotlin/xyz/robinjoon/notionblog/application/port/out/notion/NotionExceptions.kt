package xyz.robinjoon.notionblog.application.port.out.notion

sealed class NotionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class RetryableNotionException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : NotionException(message, cause)

class NotionAuthenticationException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null,
) : NotionException(message, cause)

class NotionConfigurationException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : NotionException(message, cause)
