package xyz.robinjoon.notionblog.adapter.output.notion

import xyz.robinjoon.notionblog.application.port.output.source.RetryableSourceException
import xyz.robinjoon.notionblog.application.port.output.source.SourceAccessException
import xyz.robinjoon.notionblog.application.port.output.source.SourceAuthenticationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceConfigurationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceException

internal class NotionFailureTranslator {
    fun httpFailure(statusCode: Int): SourceException = when {
        statusCode == TOO_MANY_REQUESTS || statusCode >= SERVER_ERROR_START ->
            RetryableSourceException("Notion source request failed with HTTP $statusCode")

        statusCode == UNAUTHORIZED || statusCode == FORBIDDEN ->
            SourceAuthenticationException("Notion source authentication failed with HTTP $statusCode")

        statusCode == NOT_FOUND ->
            SourceAccessException("Notion source object could not be accessed")

        else -> SourceConfigurationException("Notion source rejected a request with HTTP $statusCode")
    }

    fun requestFailure(): RetryableSourceException = RetryableSourceException("Notion source request could not be completed")

    fun invalidResponse(): SourceConfigurationException = SourceConfigurationException("Notion source returned an invalid response")

    fun collectionDeadlineExceeded(): RetryableSourceException = RetryableSourceException("Notion source collection deadline exceeded")

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR_START = 500
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }
}
