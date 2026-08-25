package xyz.robinjoon.notionblog.adapter.input.scheduling

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.notionblog.application.service.SynchronizationQueryService
import xyz.robinjoon.notionblog.application.service.SynchronizePostService
import xyz.robinjoon.notionblog.application.service.SynchronizePublicationService
import xyz.robinjoon.notionblog.application.service.SynchronizeSiteConfigurationService
import xyz.robinjoon.notionblog.domain.sync.SyncTarget
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

@ConditionalOnProperty(
    prefix = "blog.synchronization",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SynchronizationScheduler(
    private val queryService: SynchronizationQueryService,
    private val siteConfigurationService: SynchronizeSiteConfigurationService,
    private val publicationService: SynchronizePublicationService,
    private val postService: SynchronizePostService,
    private val clock: Clock,
    private val dueBatchSize: Int,
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(SynchronizationScheduler::class.java)

    init {
        require(dueBatchSize > 0) { "due batch size must be positive" }
    }

    @Scheduled(fixedDelayString = "\${blog.synchronization.interval-ms:60000}")
    fun synchronizeDue() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        try {
            val targets = try {
                queryService.findDueTargets(clock.instant(), dueBatchSize)
            } catch (exception: RuntimeException) {
                logFailure("LOOKUP", exception)
                return
            }

            targets.forEach { target ->
                try {
                    dispatch(target)
                } catch (exception: RuntimeException) {
                    logFailure(target.kind(), exception)
                }
            }
        } finally {
            running.set(false)
        }
    }

    private fun dispatch(target: SyncTarget) {
        when (target) {
            SyncTarget.SiteConfiguration -> siteConfigurationService.synchronize()
            is SyncTarget.Publication -> publicationService.synchronize()
            is SyncTarget.Post -> postService.synchronize(target.postId)
        }
    }

    private fun logFailure(targetKind: String, exception: RuntimeException) {
        logger.error(
            "Synchronization target failed targetKind={} errorType={}",
            targetKind,
            exception::class.simpleName ?: "RuntimeException",
        )
    }

    private fun SyncTarget.kind(): String = when (this) {
        SyncTarget.SiteConfiguration -> "SITE_CONFIGURATION"
        is SyncTarget.Publication -> "PUBLICATION"
        is SyncTarget.Post -> "POST"
    }
}
