package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshot
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.PageRouteKind

class PageAccessServiceTest {
    @Test
    fun `returns stale cached public content and asks the page refresher without blocking`() {
        val now = Instant.parse("2026-07-01T00:00:00Z")
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val snapshot = PublicPageSnapshot(pageId, "Post", "[]", now, now, now.minusSeconds(1))
        val persistence = AccessPersistence(pageId, snapshot, known = true)
        val refreshed = mutableListOf<NotionPageId>()
        val service = PageAccessService(persistence, PageRefreshRequester { refreshed += it }, Clock.fixed(now, ZoneOffset.UTC), Codec, RefreshPageUseCase { true })

        assertThat(service.lookup("post")).isEqualTo(PageLookupResult.Page(pageId, "Post", emptyList(), snapshot))
        assertThat(refreshed).containsExactly(pageId)
    }

    @Test
    fun `lazy collection rejects unknown ids and redirects only known ids`() {
        val pageId = NotionPageId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        val persistence = AccessPersistence(pageId, null, known = false)
        val service = PageAccessService(persistence, PageRefreshRequester { }, Clock.systemUTC(), Codec, RefreshPageUseCase { true })

        assertThat(service.collectKnownPage(pageId.value)).isEqualTo(LazyCollectionResult.NotFound)
    }

    @Test
    fun `lazy collection synchronously refreshes a known page before resolving its new route`() {
        val pageId = NotionPageId("cccccccccccccccccccccccccccccccc")
        val persistence = AccessPersistence(pageId, null, known = true)
        val service = PageAccessService(
            persistence,
            PageRefreshRequester { },
            Clock.systemUTC(),
            Codec,
            RefreshPageUseCase { persistence.routeAvailable = true; true },
        )

        assertThat(service.collectKnownPage(pageId.value)).isEqualTo(LazyCollectionResult.Redirect("/post"))
    }

    private class AccessPersistence(
        private val pageId: NotionPageId,
        private val snapshot: PublicPageSnapshot?,
        private val known: Boolean,
    ) : BlogPersistencePort {
        var routeAvailable = false
        override fun resolveRoute(path: String): ResolvedRoute? = ResolvedRoute.Page(pageId, path)
        override fun findPublicPageSnapshot(pageId: NotionPageId): PublicPageSnapshot? = snapshot
        override fun findDuePageIds(now: Instant, limit: Int): List<NotionPageId> = emptyList()
        override fun findDueSettingsDataSourceIds(now: Instant, limit: Int): List<String> = emptyList()
        override fun recordDiscoveredPage(pageId: NotionPageId, refreshAfter: Instant) = Unit
        override fun savePublicPageSnapshot(snapshot: PublicPageSnapshotWrite) = Unit
        override fun saveSettings(settings: SiteSettingsWrite) = Unit
        override fun makePagePrivate(pageId: NotionPageId, refreshAfter: Instant, lastError: String?) = Unit
        override fun touchPublicPage(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant) = Unit
        override fun isKnownPage(pageId: NotionPageId): Boolean = known
        override fun findRoutesForPage(pageId: NotionPageId): List<PageRoute> =
            if (routeAvailable) listOf(PageRoute("/post", pageId, PageRouteKind.CANONICAL)) else emptyList()
    }

    private object Codec : PageSnapshotCodec {
        override fun encode(blocks: List<xyz.robinjoon.notionblog.domain.model.NotionBlock>): String = "[]"
        override fun decode(snapshotJson: String): List<xyz.robinjoon.notionblog.domain.model.NotionBlock> = emptyList()
    }
}
