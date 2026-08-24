package xyz.robinjoon.notionblog.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import java.time.Instant

@Transactional
class TransactionalPageStore(
    private val persistence: BlogPersistencePort,
    private val remoteCallActive: () -> Boolean = { false },
) {
    fun save(snapshot: PublicPageSnapshotWrite, discoveredPageIds: List<NotionPageId>) {
        check(!remoteCallActive()) { "Notion calls must finish before persistence transaction starts" }
        persistence.savePublicPageSnapshot(snapshot)
        discoveredPageIds.distinct().forEach { persistence.recordDiscoveredPage(it, snapshot.capturedAt) }
    }

    fun makePrivate(pageId: NotionPageId, refreshAfter: Instant) {
        check(!remoteCallActive()) { "Notion calls must finish before persistence transaction starts" }
        persistence.makePagePrivate(pageId, refreshAfter)
    }

    fun touch(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant) {
        persistence.touchPublicPage(pageId, syncedAt, refreshAfter)
    }

    fun recordFailure(pageId: NotionPageId, failureCount: Int, refreshAfter: Instant, lastError: String) {
        persistence.recordPageFailure(pageId, failureCount, refreshAfter, lastError)
    }
}
