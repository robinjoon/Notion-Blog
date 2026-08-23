package xyz.robinjoon.notionblog.scheduling

import java.time.Clock
import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort

class RefreshScheduler(
    private val persistence: BlogPersistencePort,
    private val pageRefresh: RefreshPageUseCase,
    private val settingsRefresh: Map<String, RefreshSettingsUseCase>,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${blog.refresh.fixed-delay-ms:60000}")
    fun refreshDue() {
        val now = clock.instant()
        settingsRefresh.forEach { (dataSourceId, refresh) ->
            if (runCatching { !persistence.hasSettings(dataSourceId) }.getOrDefault(false)) {
                runCatching { refresh.refresh() }
            }
        }
        runCatching { persistence.findDueSettingsDataSourceIds(now, BATCH_SIZE) }.getOrDefault(emptyList()).forEach { dataSourceId ->
            settingsRefresh[dataSourceId]?.let { refresh -> runCatching { refresh.refresh() } }
        }
        runCatching { persistence.findDuePageIds(now, BATCH_SIZE) }.getOrDefault(emptyList()).forEach { pageId -> runCatching { pageRefresh.refresh(pageId) } }
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}
