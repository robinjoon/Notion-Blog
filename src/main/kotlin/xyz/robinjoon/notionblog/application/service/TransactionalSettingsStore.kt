package xyz.robinjoon.notionblog.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import java.time.Instant

@Transactional
class TransactionalSettingsStore(
    private val persistence: BlogPersistencePort,
) {
    fun save(settings: SiteSettingsWrite, discoveredPageIds: List<NotionPageId>) {
        persistence.saveSettings(settings)
        discoveredPageIds.distinct().forEach { persistence.recordDiscoveredPage(it, settings.syncedAt) }
        persistence.replaceRootRoute(settings.rootPageId, settings.syncedAt)
    }

    fun recordFailure(settingsDataSourceId: String, failureCount: Int, refreshAfter: Instant, lastError: String) {
        persistence.recordSettingsFailure(settingsDataSourceId, failureCount, refreshAfter, lastError)
    }
}
