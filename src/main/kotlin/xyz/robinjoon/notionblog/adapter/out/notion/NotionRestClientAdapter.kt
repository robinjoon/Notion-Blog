package xyz.robinjoon.notionblog.adapter.out.notion

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import xyz.robinjoon.notionblog.application.port.out.notion.NotionAuthenticationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionConfigurationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.notion.NotionPageContent
import xyz.robinjoon.notionblog.application.port.out.notion.NotionPageMetadata
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingsRow
import xyz.robinjoon.notionblog.application.port.out.notion.RetryableNotionException
import xyz.robinjoon.notionblog.domain.model.NotionBlock
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NotionPageReference
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.Locale

class NotionRestClientAdapter(
    baseUrl: String,
    token: String,
    apiVersion: String,
    requestTimeout: Duration = Duration.ofSeconds(10),
    totalCollectionTimeout: Duration = Duration.ofSeconds(30),
) : NotionGateway {
    private val mapper = NotionJsonMapper()
    private val restClient: RestClient
    private val totalCollectionTimeoutNanos: Long

    init {
        val validatedBaseUrl = validateBaseUrl(baseUrl)
        require(token.isNotBlank()) { "Notion token must not be blank" }
        require(apiVersion.isNotBlank()) { "Notion API version must not be blank" }
        require(!requestTimeout.isZero && !requestTimeout.isNegative) { "Notion request timeout must be positive" }
        require(!totalCollectionTimeout.isZero && !totalCollectionTimeout.isNegative) {
            "Notion total collection timeout must be positive"
        }
        totalCollectionTimeoutNanos = totalCollectionTimeout.toNanos()

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
            .defaultHeader("Notion-Version", apiVersion)
            .build()
    }

    override fun retrievePage(pageId: NotionPageId): NotionPageMetadata {
        val response = execute("retrieve page") {
            restClient.get()
                .uri("/pages/{pageId}", pageId.value)
                .retrieve()
                .body(JsonNode::class.java)
        }
        return mapResponse("page metadata") { mapper.mapPageMetadata(response) }
    }

    override fun retrievePageContent(pageId: NotionPageId): NotionPageContent {
        val collectionStartedAt = System.nanoTime()
        val linkedPageIds = linkedSetOf<NotionPageId>()
        val blocks = collectBlocks(pageId.value, linkedPageIds, collectionStartedAt)
        checkCollectionDeadline(collectionStartedAt)
        linkedPageIds.remove(pageId)
        return NotionPageContent(blocks = blocks, linkedPageIds = linkedPageIds.toList())
    }

    override fun querySettingsDataSource(dataSourceId: String): List<NotionSettingsRow> {
        if (dataSourceId.isBlank()) {
            throw NotionConfigurationException("Notion data source ID must not be blank")
        }

        val rows = mutableListOf<NotionSettingsRow>()
        var cursor: String? = null
        do {
            val requestBody = buildMap<String, Any> {
                put("page_size", PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            }
            val response = execute("query settings data source") {
                restClient.post()
                    .uri("/data_sources/{dataSourceId}/query", dataSourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode::class.java)
            }
            rows += mapResponse("settings row") { response.path("results").toList().map(mapper::mapSettingsRow) }
            cursor = nextCursor(response)
        } while (cursor != null)
        return rows
    }

    private fun collectBlocks(
        blockId: String,
        linkedPageIds: LinkedHashSet<NotionPageId>,
        collectionStartedAt: Long,
    ): List<NotionBlock> {
        checkCollectionDeadline(collectionStartedAt)
        val rawBlocks = retrieveAllBlockChildren(blockId, collectionStartedAt)
        return rawBlocks.map { block ->
            checkCollectionDeadline(collectionStartedAt)
            mapper.collectLinkedPageIds(block, linkedPageIds)
            val children = if (block.path("has_children").asBoolean(false)) {
                val childId = requiredText(block, "id", "block")
                collectBlocks(childId, linkedPageIds, collectionStartedAt)
            } else {
                emptyList()
            }
            mapResponse("block") { mapper.mapBlock(block, children) }
        }
    }

    private fun retrieveAllBlockChildren(blockId: String, collectionStartedAt: Long): List<JsonNode> {
        val blocks = mutableListOf<JsonNode>()
        var cursor: String? = null
        do {
            checkCollectionDeadline(collectionStartedAt)
            val response = execute("retrieve block children") {
                restClient.get()
                    .uri { builder ->
                        builder.path("/blocks/{blockId}/children")
                            .queryParam("page_size", PAGE_SIZE)
                            .apply { cursor?.let { queryParam("start_cursor", it) } }
                            .build(blockId)
                    }
                    .retrieve()
                    .body(JsonNode::class.java)
            }
            checkCollectionDeadline(collectionStartedAt)
            blocks += mapResponse("block children") { response.path("results").toList() }
            cursor = nextCursor(response)
        } while (cursor != null)
        return blocks
    }

    private fun checkCollectionDeadline(collectionStartedAt: Long) {
        if (System.nanoTime() - collectionStartedAt >= totalCollectionTimeoutNanos) {
            throw RetryableNotionException("Notion block collection deadline exceeded")
        }
    }

    private fun nextCursor(response: JsonNode): String? {
        if (!response.path("has_more").asBoolean(false)) {
            return null
        }
        return response.path("next_cursor").asString("").takeIf(String::isNotBlank)
            ?: throw NotionConfigurationException("Notion response indicated another page without a cursor")
    }

    private fun execute(operation: String, request: () -> JsonNode?): JsonNode = try {
        request() ?: throw NotionConfigurationException("Notion returned an empty response while attempting to $operation")
    } catch (exception: RestClientResponseException) {
        throw classifyHttpFailure(operation, exception.statusCode.value())
    } catch (exception: ResourceAccessException) {
        throw RetryableNotionException("Notion request failed while attempting to $operation", cause = exception)
    } catch (exception: RestClientException) {
        throw NotionConfigurationException("Notion returned an invalid response while attempting to $operation")
    }

    private fun classifyHttpFailure(operation: String, statusCode: Int): RuntimeException = when {
        statusCode == 429 || statusCode >= 500 ->
            RetryableNotionException("Notion request failed while attempting to $operation", statusCode)

        statusCode == 401 || statusCode == 403 ->
            NotionAuthenticationException("Notion authentication or permission failed while attempting to $operation", statusCode)

        else ->
            NotionConfigurationException("Notion rejected the request while attempting to $operation", statusCode)
    }

    private fun <T> mapResponse(description: String, mapping: () -> T): T = try {
        mapping()
    } catch (exception: NotionConfigurationException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw NotionConfigurationException("Notion returned invalid $description data")
    }

    private fun requiredText(node: JsonNode, field: String, description: String): String = node.path(field).asString("").takeIf(String::isNotBlank)
        ?: throw NotionConfigurationException("Notion $description is missing $field")

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
        const val PAGE_SIZE = 100
        const val OFFICIAL_HOST = "api.notion.com"
        const val HTTPS_PORT = 443
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
    }
}
