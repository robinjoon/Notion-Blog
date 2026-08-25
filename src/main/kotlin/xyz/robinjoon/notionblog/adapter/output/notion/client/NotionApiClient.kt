package xyz.robinjoon.notionblog.adapter.output.notion.client

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import xyz.robinjoon.notionblog.adapter.output.notion.NotionFailureTranslator
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionBlockEnvelope
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionPageParentResponse
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionPageResponse
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionPaginationResponse
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionSettingsRowResponse
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.Locale

internal class NotionApiClient(
    baseUrl: String,
    token: String,
    requestTimeout: Duration,
    collectionTimeout: Duration,
) {
    private val restClient: RestClient
    private val collectionTimeoutNanos: Long
    private val failureTranslator = NotionFailureTranslator()

    init {
        val validatedBaseUrl = validateBaseUrl(baseUrl)
        require(token.isNotBlank()) { "Notion token must not be blank" }
        require(requestTimeout.isPositive) { "Notion request timeout must be positive" }
        require(collectionTimeout.isPositive) { "Notion collection timeout must be positive" }
        collectionTimeoutNanos = collectionTimeout.toNanos()

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(requestTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(requestTimeout)
        }
        restClient = RestClient.builder()
            .baseUrl(validatedBaseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .defaultHeader(NOTION_VERSION_HEADER, SUPPORTED_API_VERSION)
            .build()
    }

    fun fetchPage(pageId: String): NotionPageResponse {
        require(pageId.isNotBlank()) { "Notion page ID must not be blank" }
        val response = execute {
            restClient.get()
                .uri("/pages/{pageId}", pageId)
                .retrieve()
                .body(JsonNode::class.java)
        }
        return parsePage(response)
    }

    fun fetchDirectBlockChildren(blockId: String): List<NotionBlockEnvelope> {
        require(blockId.isNotBlank()) { "Notion block ID must not be blank" }
        val startedAt = System.nanoTime()
        return collectPages(startedAt) { cursor ->
            execute {
                restClient.get()
                    .uri { builder ->
                        builder.path("/blocks/{blockId}/children")
                            .queryParam(PAGE_SIZE_PARAMETER, PAGE_SIZE)
                            .apply { cursor?.let { queryParam(START_CURSOR_PARAMETER, it) } }
                            .build(blockId)
                    }
                    .retrieve()
                    .body(JsonNode::class.java)
            }.let(::parseBlockPage)
        }
    }

    fun fetchSettingsRows(dataSourceId: String): List<NotionSettingsRowResponse> {
        require(dataSourceId.isNotBlank()) { "Notion settings data source ID must not be blank" }
        val startedAt = System.nanoTime()
        return collectPages(startedAt) { cursor ->
            val requestBody = buildMap<String, Any> {
                put(PAGE_SIZE_PARAMETER, PAGE_SIZE)
                cursor?.let { put(START_CURSOR_PARAMETER, it) }
            }
            execute {
                restClient.post()
                    .uri("/data_sources/{dataSourceId}/query", dataSourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode::class.java)
            }.let(::parseSettingsPage)
        }
    }

    private fun <T> collectPages(
        startedAt: Long,
        fetch: (String?) -> NotionPaginationResponse<T>,
    ): List<T> {
        val results = mutableListOf<T>()
        var cursor: String? = null
        do {
            checkCollectionDeadline(startedAt)
            val page = fetch(cursor)
            checkCollectionDeadline(startedAt)
            results += page.results
            cursor = page.nextCursor
        } while (cursor != null)
        return results
    }

    private fun checkCollectionDeadline(startedAt: Long) {
        if (System.nanoTime() - startedAt >= collectionTimeoutNanos) {
            throw failureTranslator.collectionDeadlineExceeded()
        }
    }

    private fun parsePage(node: JsonNode): NotionPageResponse = try {
        NotionPageResponse(
            id = node.requiredText("id"),
            parent = parsePageParent(node.requiredObject("parent")),
            url = node.requiredText("url"),
            publicUrl = node.optionalText("public_url"),
            inTrash = node.requiredBoolean("in_trash"),
            lastEditedTime = node.requiredText("last_edited_time"),
            properties = node.requiredObject("properties"),
        )
    } catch (exception: IllegalArgumentException) {
        throw failureTranslator.invalidResponse()
    }

    private fun parsePageParent(node: JsonNode): NotionPageParentResponse = NotionPageParentResponse(
        type = node.requiredText("type"),
        pageId = node.optionalText("page_id"),
    )

    private fun parseBlockPage(node: JsonNode): NotionPaginationResponse<NotionBlockEnvelope> = parsePagination(node) { block ->
        NotionBlockEnvelope(
            id = block.requiredText("id"),
            type = block.requiredText("type"),
            hasChildren = block.requiredBoolean("has_children"),
            inTrash = block.requiredBoolean("in_trash"),
            payload = block.requiredObject(block.requiredText("type")),
        )
    }

    private fun parseSettingsPage(node: JsonNode): NotionPaginationResponse<NotionSettingsRowResponse> = parsePagination(node) { row ->
        NotionSettingsRowResponse(
            id = row.requiredText("id"),
            properties = row.requiredObject("properties"),
        )
    }

    private fun <T> parsePagination(
        node: JsonNode,
        parseResult: (JsonNode) -> T,
    ): NotionPaginationResponse<T> = try {
        val results = node.requiredArray("results").toList().map(parseResult)
        NotionPaginationResponse(
            results = results,
            hasMore = node.requiredBoolean("has_more"),
            nextCursor = node.optionalCursor(),
        )
    } catch (exception: IllegalArgumentException) {
        throw failureTranslator.invalidResponse()
    }

    private fun execute(request: () -> JsonNode?): JsonNode = try {
        request() ?: throw failureTranslator.invalidResponse()
    } catch (exception: RestClientResponseException) {
        throw failureTranslator.httpFailure(exception.statusCode.value())
    } catch (exception: ResourceAccessException) {
        throw failureTranslator.requestFailure()
    } catch (exception: RestClientException) {
        throw failureTranslator.invalidResponse()
    }

    private fun JsonNode.requiredText(field: String): String = optionalText(field)
        ?: throw IllegalArgumentException("Missing required field")

    private fun JsonNode.optionalText(field: String): String? = get(field)
        ?.takeUnless(JsonNode::isNull)
        ?.takeIf(JsonNode::isString)
        ?.stringValue()
        ?.takeIf(String::isNotBlank)

    private fun JsonNode.optionalCursor(): String? {
        val cursor = get("next_cursor") ?: return null
        if (cursor.isNull) {
            return null
        }
        return cursor.takeIf(JsonNode::isString)?.stringValue()
            ?: throw IllegalArgumentException("Invalid pagination cursor")
    }

    private fun JsonNode.requiredBoolean(field: String): Boolean = get(field)
        ?.takeIf(JsonNode::isBoolean)
        ?.asBoolean()
        ?: throw IllegalArgumentException("Missing required field")

    private fun JsonNode.requiredObject(field: String): JsonNode = get(field)
        ?.takeIf(JsonNode::isObject)
        ?: throw IllegalArgumentException("Missing required object")

    private fun JsonNode.requiredArray(field: String): JsonNode = get(field)
        ?.takeIf(JsonNode::isArray)
        ?: throw IllegalArgumentException("Missing required array")

    private fun validateBaseUrl(value: String): String {
        require(value.isNotBlank()) { "Notion base URL must not be blank" }
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri != null && uri.isAbsolute && uri.host != null) { "Notion base URL must be an absolute HTTP URL" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Notion base URL must not contain userinfo, a query, or a fragment"
        }

        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val host = uri.host.lowercase(Locale.ROOT)
        val isOfficialOrigin = scheme == "https" && host == OFFICIAL_HOST && (uri.port == -1 || uri.port == HTTPS_PORT)
        val isLoopbackOrigin = (scheme == "http" || scheme == "https") && host in LOOPBACK_HOSTS
        require(isOfficialOrigin || isLoopbackOrigin) {
            "Notion base URL must use the official HTTPS origin or a loopback test host"
        }
        return value.trim().trimEnd('/')
    }

    private companion object {
        const val SUPPORTED_API_VERSION = "2026-03-11"
        const val NOTION_VERSION_HEADER = "Notion-Version"
        const val PAGE_SIZE = 100
        const val PAGE_SIZE_PARAMETER = "page_size"
        const val START_CURSOR_PARAMETER = "start_cursor"
        const val OFFICIAL_HOST = "api.notion.com"
        const val HTTPS_PORT = 443
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
    }
}
