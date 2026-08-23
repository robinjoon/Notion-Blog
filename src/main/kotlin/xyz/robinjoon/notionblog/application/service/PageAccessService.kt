package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshot
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NotionPageReference

class PageAccessService(
    private val persistence: BlogPersistencePort,
    private val pageRefresh: PageRefreshRequester,
    private val clock: Clock,
    private val snapshotCodec: PageSnapshotCodec,
    private val lazyPageRefresh: RefreshPageUseCase = RefreshPageUseCase { false },
) {
    fun lookup(path: String): PageLookupResult {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        return when (val route = persistence.resolveRoute(normalizedPath)) {
            is ResolvedRoute.Redirect -> PageLookupResult.Redirect(route.destination)
            is ResolvedRoute.Page -> persistence.findPublicPageSnapshot(route.pageId)
                ?.also { snapshot -> if (!snapshot.refreshAfter.isAfter(clock.instant())) pageRefresh.request(route.pageId) }
                ?.let { PageLookupResult.Page(it.pageId, it.title, snapshotCodec.decode(it.snapshotJson), it) }
                ?: PageLookupResult.NotFound
            null -> PageLookupResult.NotFound
        }
    }

    fun collectKnownPage(reference: String): LazyCollectionResult {
        val pageId = NotionPageReference.parse(reference) ?: return LazyCollectionResult.NotFound
        if (!persistence.isKnownPage(pageId)) {
            return LazyCollectionResult.NotFound
        }
        persistence.resolveRouteForPage(pageId)?.let { return LazyCollectionResult.Redirect(it) }
        runCatching { lazyPageRefresh.refresh(pageId) }
        return when (val route = persistence.resolveRouteForPage(pageId)) {
            null -> LazyCollectionResult.NotFound
            else -> LazyCollectionResult.Redirect(route)
        }
    }
}

sealed interface PageLookupResult {
    data class Page(val pageId: NotionPageId, val title: String, val blocks: List<xyz.robinjoon.notionblog.domain.model.NotionBlock>, val snapshot: PublicPageSnapshot) : PageLookupResult
    data class Redirect(val destination: String) : PageLookupResult
    data object NotFound : PageLookupResult
}

sealed interface LazyCollectionResult {
    data class Redirect(val destination: String) : LazyCollectionResult
    data object NotFound : LazyCollectionResult
}

private fun BlogPersistencePort.resolveRouteForPage(pageId: NotionPageId): String? = findRoutesForPage(pageId)
    .firstOrNull { it.active && (it.kind == xyz.robinjoon.notionblog.domain.model.PageRouteKind.ROOT || it.kind == xyz.robinjoon.notionblog.domain.model.PageRouteKind.CANONICAL) }
    ?.path
