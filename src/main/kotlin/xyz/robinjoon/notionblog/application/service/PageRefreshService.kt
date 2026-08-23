package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.PageRouteKind
import xyz.robinjoon.notionblog.domain.model.PageRoutes
import xyz.robinjoon.notionblog.domain.model.Slug
import xyz.robinjoon.notionblog.domain.policy.RefreshPolicy
import xyz.robinjoon.notionblog.domain.policy.RefreshTargetKind

class PageRefreshService(
    private val gateway: NotionGateway,
    private val persistence: BlogPersistencePort,
    private val store: TransactionalPageStore,
    private val clock: Clock,
    private val snapshotCodec: PageSnapshotCodec,
) : RefreshPageUseCase {
    private val refreshing = ConcurrentHashMap.newKeySet<NotionPageId>()

    override fun refresh(pageId: NotionPageId): Boolean {
        if (!refreshing.add(pageId)) {
            return false
        }
        try {
            val metadata = gateway.retrievePage(pageId)
            val now = clock.instant()
            val refreshAfter = RefreshPolicy.nextRefreshAt(RefreshTargetKind.PAGE, 0, now)
            if (metadata.publicUrl == null) {
                store.makePrivate(metadata.id, refreshAfter)
                return true
            }
            val previous = persistence.findPublicPageSnapshot(metadata.id)
            if (previous?.notionLastEditedAt == metadata.lastEditedAt) {
                store.touch(metadata.id, now, refreshAfter)
                return true
            }
            val content = gateway.retrievePageContent(metadata.id)
            val routes = routesFor(metadata.id, metadata.title)
            store.save(
                PublicPageSnapshotWrite(
                    pageId = metadata.id,
                    title = metadata.title.ifBlank { "Untitled" },
                    notionUrl = metadata.notionUrl,
                    publicUrl = metadata.publicUrl,
                    notionLastEditedAt = metadata.lastEditedAt,
                    syncedAt = now,
                    refreshAfter = refreshAfter,
                    snapshotJson = snapshotCodec.encode(content.blocks),
                    capturedAt = now,
                    routes = routes,
                ),
                content.linkedPageIds,
            )
            return true
        } catch (exception: RuntimeException) {
            val failureCount = persistence.pageFailureCount(pageId) + 1
            val now = clock.instant()
            store.recordFailure(
                pageId,
                failureCount,
                RefreshPolicy.nextRefreshAt(RefreshTargetKind.PAGE, failureCount, now),
                summarize(exception),
            )
            throw exception
        } finally {
            refreshing.remove(pageId)
        }
    }

    private fun routesFor(pageId: NotionPageId, title: String): List<PageRoute> {
        if (persistence.isRootPage(pageId)) {
            return listOf(PageRoute("/", pageId, PageRouteKind.ROOT))
        }
        val existing = persistence.findRoutesForPage(pageId)
        val basePath = "/${Slug.fromTitle(title, pageId.value)}"
        val canonical = if (existing.any { it.active && it.kind == PageRouteKind.CANONICAL && it.path == basePath }) {
            basePath
        } else {
            Slug.unique(basePath, pageId.value) { path -> persistence.pathExists(path) && existing.none { it.path == path } }
        }
        return PageRoutes.changeCanonical(existing, pageId, canonical)
    }

    private fun summarize(exception: RuntimeException): String = exception::class.simpleName ?: "refresh failure"
}
