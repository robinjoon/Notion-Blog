package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import java.time.Duration
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.`in`.SettingsRefreshResult
import xyz.robinjoon.notionblog.application.port.out.notion.NotionAuthenticationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionConfigurationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingKind
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingsRow
import xyz.robinjoon.notionblog.application.port.out.notion.RetryableNotionException
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NotionPageReference
import xyz.robinjoon.notionblog.domain.policy.RefreshPolicy

class SettingsRefreshService(
    private val gateway: NotionGateway,
    private val persistence: BlogPersistencePort,
    private val settingsDataSourceId: String,
    private val clock: Clock,
    private val store: TransactionalSettingsStore = TransactionalSettingsStore(persistence),
    private val refreshInterval: Duration = Duration.ofMinutes(1),
) : RefreshSettingsUseCase {
    private val jsonMapper = JsonMapper.builder().build()
    private val logger = LoggerFactory.getLogger(SettingsRefreshService::class.java)

    override fun refresh(): SettingsRefreshResult {
        return try {
        val rows = gateway.querySettingsDataSource(settingsDataSourceId)
        val root = rows.firstEnabled("rootPage")?.page?.toPageId()
            ?: throw IllegalArgumentException(ROOT_PAGE_ERROR)
        val header = rows.firstEnabled("header")?.page?.toPageId()
        val footer = rows.firstEnabled("footer")?.page?.toPageId()
        val headJson = rows.firstEnabled("head")?.data?.let(::validatedHeadJson) ?: "{}"
        val now = clock.instant()

        val settings =
            SiteSettingsWrite(
                settingsDataSourceId = settingsDataSourceId,
                rootPageId = root,
                headerPageId = header,
                footerPageId = footer,
                headJson = headJson,
                syncedAt = now,
                refreshAfter = RefreshPolicy.nextSuccessfulRefreshAt(now, refreshInterval),
            )
        store.save(settings, listOfNotNull(root, header, footer))
        SettingsRefreshResult(root, header, footer, headJson)
        } catch (exception: RuntimeException) {
            logFailure(exception)
            val failureCount = persistence.settingsFailureCount(settingsDataSourceId) + 1
            val now = clock.instant()
            store.recordFailure(
                settingsDataSourceId,
                failureCount,
                RefreshPolicy.nextSettingsFailureAt(now, refreshInterval, failureCount),
                exception::class.simpleName ?: "refresh failure",
            )
            throw exception
        }
    }

    private fun List<NotionSettingsRow>.firstEnabled(key: String): NotionSettingsRow? =
        firstOrNull { it.enabled && it.key == key }

    private fun String.toPageId(): NotionPageId? = NotionPageReference.parse(this)

    private fun validatedHeadJson(value: String): String = try {
        jsonMapper.readTree(value).takeIf { it.isObject }?.toString()
            ?: throw IllegalArgumentException(HEAD_JSON_ERROR)
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw IllegalArgumentException(HEAD_JSON_ERROR)
    }

    private fun logFailure(exception: RuntimeException) {
        val errorType = exception::class.simpleName ?: "RuntimeException"
        val statusCode = when (exception) {
            is RetryableNotionException -> exception.statusCode
            is NotionAuthenticationException -> exception.statusCode
            is NotionConfigurationException -> exception.statusCode
            else -> null
        }
        val detail = exception.message?.takeIf { it == ROOT_PAGE_ERROR || it == HEAD_JSON_ERROR }
        val message =
            "Settings refresh failed targetType={} targetId={} errorType={} statusCode={} detail={}"
        if (exception is RetryableNotionException) {
            logger.warn(message, "settings", settingsDataSourceId, errorType, statusCode ?: "none", detail ?: "none")
        } else {
            logger.error(message, "settings", settingsDataSourceId, errorType, statusCode ?: "none", detail ?: "none")
        }
    }

    private companion object {
        const val ROOT_PAGE_ERROR = "settings rootPage must contain a supported Notion page URL or ID"
        const val HEAD_JSON_ERROR = "settings head has invalid JSON"
    }
}
