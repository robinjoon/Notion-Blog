package xyz.robinjoon.notionblog.scheduling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import java.time.Clock
import java.time.Instant

class RefreshScheduler(
    private val persistence: BlogPersistencePort,
    private val pageRefresh: RefreshPageUseCase,
    private val settingsRefresh: Map<String, RefreshSettingsUseCase>,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(RefreshScheduler::class.java)

    @Scheduled(fixedDelayString = "\${blog.refresh.interval-ms:60000}")
    fun refreshDue() {
        val now = clock.instant()
        settingsRefresh.forEach { (dataSourceId, refresh) ->
            if (hasNoSettings(dataSourceId)) {
                ignoreHandledFailure { refresh.refresh() }
            }
        }
        findDueSettings(now).forEach { dataSourceId ->
            settingsRefresh[dataSourceId]?.let { refresh -> ignoreHandledFailure { refresh.refresh() } }
        }
        findDuePages(now).forEach { pageId -> ignoreHandledFailure { pageRefresh.refresh(pageId) } }
    }

    private fun ignoreHandledFailure(refresh: () -> Unit) {
        try {
            refresh()
        } catch (_: RuntimeException) {
        }
    }

    private fun hasNoSettings(dataSourceId: String): Boolean = try {
        !persistence.hasSettings(dataSourceId)
    } catch (exception: RuntimeException) {
        logLookupFailure("hasSettings", dataSourceId, exception)
        false
    }

    private fun findDueSettings(now: Instant): List<String> = try {
        persistence.findDueSettingsDataSourceIds(now, BATCH_SIZE)
    } catch (exception: RuntimeException) {
        logLookupFailure("findDueSettings", "all", exception)
        emptyList()
    }

    private fun findDuePages(now: Instant): List<NotionPageId> = try {
        persistence.findDuePageIds(now, BATCH_SIZE)
    } catch (exception: RuntimeException) {
        logLookupFailure("findDuePages", "all", exception)
        emptyList()
    }

    private fun logLookupFailure(operation: String, targetId: String, exception: RuntimeException) {
        logger.error(
            "Refresh target lookup failed operation={} targetType=scheduler targetId={} errorType={}",
            operation,
            targetId,
            exception::class.simpleName ?: "RuntimeException",
        )
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}
