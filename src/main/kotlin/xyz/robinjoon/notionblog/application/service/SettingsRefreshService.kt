package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.`in`.SettingsRefreshResult
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingKind
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingsRow
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NotionPageReference
import xyz.robinjoon.notionblog.domain.policy.RefreshPolicy
import xyz.robinjoon.notionblog.domain.policy.RefreshTargetKind

class SettingsRefreshService(
    private val gateway: NotionGateway,
    private val persistence: BlogPersistencePort,
    private val settingsDataSourceId: String,
    private val clock: Clock,
    private val store: TransactionalSettingsStore = TransactionalSettingsStore(persistence),
) : RefreshSettingsUseCase {
    private val jsonMapper = JsonMapper.builder().build()

    override fun refresh(): SettingsRefreshResult {
        return try {
        val rows = gateway.querySettingsDataSource(settingsDataSourceId)
        val root = rows.firstEnabled("rootPage")?.page?.toPageId()
            ?: throw IllegalArgumentException("settings rootPage is required")
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
                refreshAfter = RefreshPolicy.nextRefreshAt(RefreshTargetKind.SETTINGS, 0, now),
            )
        store.save(settings, listOfNotNull(root, header, footer))
        SettingsRefreshResult(root, header, footer, headJson)
        } catch (exception: RuntimeException) {
            val failureCount = persistence.settingsFailureCount(settingsDataSourceId) + 1
            val now = clock.instant()
            store.recordFailure(
                settingsDataSourceId,
                failureCount,
                RefreshPolicy.nextRefreshAt(RefreshTargetKind.SETTINGS, failureCount, now),
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
            ?: throw IllegalArgumentException("settings head has invalid JSON")
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw IllegalArgumentException("settings head has invalid JSON")
    }
}
