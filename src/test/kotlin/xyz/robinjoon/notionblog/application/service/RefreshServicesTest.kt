package xyz.robinjoon.notionblog.application.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.notion.NotionPageContent
import xyz.robinjoon.notionblog.application.port.out.notion.NotionPageMetadata
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingKind
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingsRow
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshot
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute

class RefreshServicesTest {
    private val now = Instant.parse("2026-07-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `settings refresh validates root head JSON and discovers its referenced pages`() {
        val persistence = RecordingPersistence()
        val gateway = Gateway(settingsRows = listOf(
            row("rootPage", NotionSettingKind.PAGE, "0123456789abcdef0123456789abcdef"),
            row("header", NotionSettingKind.BLOCKS, "11111111111111111111111111111111"),
            row("footer", NotionSettingKind.BLOCKS, "22222222222222222222222222222222"),
            row("head", NotionSettingKind.HEAD, data = "{\"siteName\":\"Blog\"}"),
        ))

        SettingsRefreshService(gateway, persistence, "settings-db", clock).refresh()

        assertThat(persistence.settings?.rootPageId?.value).isEqualTo("0123456789abcdef0123456789abcdef")
        assertThat(persistence.discovered.map(NotionPageId::value)).containsExactlyInAnyOrder(
            "0123456789abcdef0123456789abcdef",
            "11111111111111111111111111111111",
            "22222222222222222222222222222222",
        )
    }

    @Test
    fun `settings refresh rejects a missing root page`() {
        val gateway = Gateway(settingsRows = listOf(row("head", NotionSettingKind.HEAD, data = "{}")))

        assertThatThrownBy { SettingsRefreshService(gateway, RecordingPersistence(), "settings-db", clock).refresh() }
            .hasMessage("settings rootPage is required")
    }

    @Test
    fun `page refresh fetches Notion outside the transactional store and saves canonical snapshot`() {
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val persistence = RecordingPersistence()
        val gateway = Gateway(metadata = NotionPageMetadata(pageId, "Hello", "https://www.notion.so/hello", "https://www.notion.so/hello", now))
        val store = TransactionalPageStore(persistence) { gateway.inRemoteCall }

        PageRefreshService(gateway, persistence, store, clock, SnapshotCodec).refresh(pageId)

        assertThat(gateway.contentCalls).isEqualTo(1)
        assertThat(persistence.savedSnapshot?.routes).containsExactly(PageRoute("/hello", pageId, xyz.robinjoon.notionblog.domain.model.PageRouteKind.CANONICAL))
        assertThat(persistence.savedSnapshot?.refreshAfter).isEqualTo(now.plusSeconds(900))
    }

    @Test
    fun `private pages skip block collection and atomically deactivate routes`() {
        val pageId = NotionPageId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        val persistence = RecordingPersistence()
        val gateway = Gateway(metadata = NotionPageMetadata(pageId, "Private", "https://www.notion.so/private", null, now))
        val store = TransactionalPageStore(persistence) { gateway.inRemoteCall }

        PageRefreshService(gateway, persistence, store, clock, SnapshotCodec).refresh(pageId)

        assertThat(gateway.contentCalls).isZero()
        assertThat(persistence.privatePageId).isEqualTo(pageId)
    }

    @Test
    fun `unchanged public metadata skips block retrieval`() {
        val pageId = NotionPageId("cccccccccccccccccccccccccccccccc")
        val persistence = RecordingPersistence().apply {
            previous = PublicPageSnapshot(pageId, "Same", "[]", now, now, now.plusSeconds(900))
        }
        val gateway = Gateway(metadata = NotionPageMetadata(pageId, "Same", "https://www.notion.so/same", "https://www.notion.so/same", now))

        PageRefreshService(gateway, persistence, TransactionalPageStore(persistence), clock, SnapshotCodec).refresh(pageId)

        assertThat(gateway.contentCalls).isZero()
    }

    @Test
    fun `page refresh records linked page ids as discovered`() {
        val pageId = NotionPageId("dddddddddddddddddddddddddddddddd")
        val linked = NotionPageId("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee")
        val persistence = RecordingPersistence()
        val gateway = Gateway(
            metadata = NotionPageMetadata(pageId, "Links", "https://www.notion.so/links", "https://www.notion.so/links", now),
            content = NotionPageContent(emptyList(), listOf(linked)),
        )

        PageRefreshService(gateway, persistence, TransactionalPageStore(persistence), clock, SnapshotCodec).refresh(pageId)

        assertThat(persistence.discovered).contains(linked)
    }

    @Test
    fun `page refresh records increasing failure counts with exponential backoff`() {
        val pageId = NotionPageId("ffffffffffffffffffffffffffffffff")
        val persistence = RecordingPersistence().apply { pageFailures = 1 }
        val failingGateway = Gateway(metadataFailure = IllegalStateException("timeout token=secret-body"))
        val service = PageRefreshService(failingGateway, persistence, TransactionalPageStore(persistence), clock, SnapshotCodec)

        assertThatThrownBy { service.refresh(pageId) }.isInstanceOf(IllegalStateException::class.java)

        assertThat(persistence.pageFailure).isEqualTo(PageFailure(pageId, 2, now.plusSeconds(1_500), "IllegalStateException"))
    }

    @Test
    fun `settings refresh records its failure backoff`() {
        val persistence = RecordingPersistence().apply { settingsFailures = 1 }
        val gateway = Gateway(settingsFailure = IllegalStateException("Notion request failed"))

        assertThatThrownBy { SettingsRefreshService(gateway, persistence, "settings-db", clock).refresh() }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(persistence.settingsFailure).isEqualTo(SettingsFailure("settings-db", 2, now.plusSeconds(660), "IllegalStateException"))
    }

    private fun row(key: String, kind: NotionSettingKind, page: String = "", data: String = "") =
        NotionSettingsRow(key, kind, enabled = true, page = page, data = data)

    private class Gateway(
        private val settingsRows: List<NotionSettingsRow> = emptyList(),
        private val metadata: NotionPageMetadata? = null,
        private val content: NotionPageContent = NotionPageContent(emptyList(), emptyList()),
        private val metadataFailure: RuntimeException? = null,
        private val settingsFailure: RuntimeException? = null,
    ) : NotionGateway {
        var inRemoteCall = false
        var contentCalls = 0

        override fun retrievePage(pageId: NotionPageId): NotionPageMetadata = remote { metadataFailure?.let { throw it }; requireNotNull(metadata) }

        override fun retrievePageContent(pageId: NotionPageId): NotionPageContent = remote {
            contentCalls += 1
            content
        }

        override fun querySettingsDataSource(dataSourceId: String): List<NotionSettingsRow> = remote { settingsFailure?.let { throw it }; settingsRows }

        private fun <T> remote(block: () -> T): T {
            inRemoteCall = true
            return try { block() } finally { inRemoteCall = false }
        }
    }

    private class RecordingPersistence : BlogPersistencePort {
        val discovered = mutableListOf<NotionPageId>()
        var settings: SiteSettingsWrite? = null
        var savedSnapshot: PublicPageSnapshotWrite? = null
        var privatePageId: NotionPageId? = null
        var previous: PublicPageSnapshot? = null
        var pageFailures = 0
        var settingsFailures = 0
        var pageFailure: PageFailure? = null
        var settingsFailure: SettingsFailure? = null

        override fun resolveRoute(path: String): ResolvedRoute? = null
        override fun findPublicPageSnapshot(pageId: NotionPageId): PublicPageSnapshot? = previous
        override fun findDuePageIds(now: Instant, limit: Int): List<NotionPageId> = emptyList()
        override fun findDueSettingsDataSourceIds(now: Instant, limit: Int): List<String> = emptyList()
        override fun recordDiscoveredPage(pageId: NotionPageId, refreshAfter: Instant) { discovered += pageId }
        override fun savePublicPageSnapshot(snapshot: PublicPageSnapshotWrite) { savedSnapshot = snapshot }
        override fun saveSettings(settings: SiteSettingsWrite) { this.settings = settings }
        override fun makePagePrivate(pageId: NotionPageId, refreshAfter: Instant, lastError: String?) { privatePageId = pageId }
        override fun touchPublicPage(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant) = Unit
        override fun pageFailureCount(pageId: NotionPageId): Int = pageFailures
        override fun settingsFailureCount(settingsDataSourceId: String): Int = settingsFailures
        override fun recordPageFailure(pageId: NotionPageId, failureCount: Int, refreshAfter: Instant, lastError: String) {
            pageFailure = PageFailure(pageId, failureCount, refreshAfter, lastError)
        }
        override fun recordSettingsFailure(settingsDataSourceId: String, failureCount: Int, refreshAfter: Instant, lastError: String) {
            settingsFailure = SettingsFailure(settingsDataSourceId, failureCount, refreshAfter, lastError)
        }
    }

    private object SnapshotCodec : PageSnapshotCodec {
        override fun encode(blocks: List<xyz.robinjoon.notionblog.domain.model.NotionBlock>): String = "[]"
        override fun decode(snapshotJson: String): List<xyz.robinjoon.notionblog.domain.model.NotionBlock> = emptyList()
    }

    private data class PageFailure(val pageId: NotionPageId, val count: Int, val refreshAfter: Instant, val error: String)
    private data class SettingsFailure(val dataSourceId: String, val count: Int, val refreshAfter: Instant, val error: String)
}
