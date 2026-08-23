package xyz.robinjoon.notionblog.adapter.out.persistence

import java.time.Instant
import java.time.ZoneOffset
import org.springframework.transaction.annotation.Transactional
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshot
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.PageRouteKind
import xyz.robinjoon.notionblog.domain.model.PageVisibility

@Transactional(readOnly = true)
class ExposedBlogPersistenceAdapter : BlogPersistencePort {
    override fun resolveRoute(path: String): ResolvedRoute? {
        val route = PageRouteTable.selectAll()
            .where { (PageRouteTable.path eq path) and (PageRouteTable.active eq true) }
            .singleOrNull()
            ?: return null
        val pageId = NotionPageId(route[PageRouteTable.pageId])

        return when (route[PageRouteTable.kind]) {
            PageRouteKind.ROOT.name, PageRouteKind.CANONICAL.name -> ResolvedRoute.Page(pageId, path)
            PageRouteKind.ALIAS.name -> canonicalPathFor(pageId)?.let(ResolvedRoute::Redirect)
            else -> null
        }
    }

    override fun findPublicPageSnapshot(pageId: NotionPageId): PublicPageSnapshot? {
        val page = NotionPageTable.selectAll()
            .where { (NotionPageTable.pageId eq pageId.value) and (NotionPageTable.visibility eq PageVisibility.PUBLIC.name) }
            .singleOrNull()
            ?: return null
        val snapshot = PageSnapshotTable.selectAll()
            .where { PageSnapshotTable.pageId eq pageId.value }
            .singleOrNull()
            ?: return null

        return PublicPageSnapshot(
            pageId = pageId,
            title = page[NotionPageTable.title],
            snapshotJson = snapshot[PageSnapshotTable.snapshotJson],
            notionLastEditedAt = snapshot[PageSnapshotTable.notionLastEditedAt].toInstant(),
            capturedAt = snapshot[PageSnapshotTable.capturedAt].toInstant(),
            refreshAfter = page[NotionPageTable.refreshAfter].toInstant(),
        )
    }

    override fun findDuePageIds(now: Instant, limit: Int): List<NotionPageId> {
        require(limit > 0) { "limit must be positive" }
        return NotionPageTable.selectAll()
            .where { NotionPageTable.refreshAfter lessEq now.offset() }
            .limit(limit)
            .map { NotionPageId(it[NotionPageTable.pageId]) }
    }

    override fun findDueSettingsDataSourceIds(now: Instant, limit: Int): List<String> {
        require(limit > 0) { "limit must be positive" }
        return SiteSettingsTable.selectAll()
            .where { SiteSettingsTable.refreshAfter lessEq now.offset() }
            .limit(limit)
            .map { it[SiteSettingsTable.settingsDataSourceId] }
    }

    override fun hasSettings(settingsDataSourceId: String): Boolean = SiteSettingsTable.selectAll()
        .where { SiteSettingsTable.settingsDataSourceId eq settingsDataSourceId }
        .limit(1)
        .firstOrNull() != null

    override fun replaceRootRoute(pageId: NotionPageId, changedAt: Instant) {
        val existing = PageRouteTable.selectAll().where { PageRouteTable.path eq "/" }.singleOrNull()
        if (existing == null) {
            PageRouteTable.insert {
                it[path] = "/"
                it[PageRouteTable.pageId] = pageId.value
                it[kind] = PageRouteKind.ROOT.name
                it[active] = true
                it[createdAt] = changedAt.offset()
            }
        } else {
            PageRouteTable.update({ PageRouteTable.path eq "/" }) {
                it[PageRouteTable.pageId] = pageId.value
                it[kind] = PageRouteKind.ROOT.name
                it[active] = true
            }
        }
    }

    override fun findRoutesForPage(pageId: NotionPageId): List<PageRoute> = PageRouteTable.selectAll()
        .where { PageRouteTable.pageId eq pageId.value }
        .map {
            PageRoute(
                path = it[PageRouteTable.path],
                pageId = pageId,
                kind = PageRouteKind.valueOf(it[PageRouteTable.kind]),
                active = it[PageRouteTable.active],
            )
        }

    override fun pathExists(path: String): Boolean = PageRouteTable.selectAll()
        .where { PageRouteTable.path eq path }
        .limit(1)
        .firstOrNull() != null

    override fun isKnownPage(pageId: NotionPageId): Boolean = NotionPageTable.selectAll()
        .where { NotionPageTable.pageId eq pageId.value }
        .limit(1)
        .firstOrNull() != null

    override fun isRootPage(pageId: NotionPageId): Boolean = SiteSettingsTable.selectAll()
        .where { SiteSettingsTable.rootPageId eq pageId.value }
        .limit(1)
        .firstOrNull() != null

    override fun recordDiscoveredPage(pageId: NotionPageId, refreshAfter: Instant) {
        NotionPageTable.insertIgnore {
            it[NotionPageTable.pageId] = pageId.value
            it[title] = "Untitled"
            it[notionUrl] = "https://www.notion.so/${pageId.value}"
            it[publicUrl] = null
            it[visibility] = PageVisibility.DISCOVERED.name
            it[notionLastEditedAt] = null
            it[lastSyncedAt] = null
            it[NotionPageTable.refreshAfter] = refreshAfter.offset()
            it[failureCount] = 0
            it[lastError] = null
        }
    }

    override fun savePublicPageSnapshot(snapshot: PublicPageSnapshotWrite) {
        NotionPageTable.upsert(NotionPageTable.pageId) {
            it[pageId] = snapshot.pageId.value
            it[title] = snapshot.title
            it[notionUrl] = snapshot.notionUrl
            it[publicUrl] = snapshot.publicUrl
            it[visibility] = PageVisibility.PUBLIC.name
            it[notionLastEditedAt] = snapshot.notionLastEditedAt.offset()
            it[lastSyncedAt] = snapshot.syncedAt.offset()
            it[refreshAfter] = snapshot.refreshAfter.offset()
            it[failureCount] = 0
            it[lastError] = null
        }
        PageSnapshotTable.upsert(PageSnapshotTable.pageId) {
            it[pageId] = snapshot.pageId.value
            it[snapshotJson] = snapshot.snapshotJson
            it[notionLastEditedAt] = snapshot.notionLastEditedAt.offset()
            it[capturedAt] = snapshot.capturedAt.offset()
        }
        snapshot.routes.forEach { route ->
            require(route.pageId == snapshot.pageId) { "all routes must belong to the snapshot page" }
            val existing = PageRouteTable.selectAll()
                .where { PageRouteTable.path eq route.path }
                .singleOrNull()
            when {
                existing == null -> PageRouteTable.insert {
                    it[path] = route.path
                    it[pageId] = route.pageId.value
                    it[kind] = route.kind.name
                    it[active] = route.active
                    it[createdAt] = snapshot.capturedAt.offset()
                }
                existing[PageRouteTable.pageId] == route.pageId.value -> PageRouteTable.update({ PageRouteTable.path eq route.path }) {
                    it[kind] = route.kind.name
                    it[active] = route.active
                }
                else -> throw IllegalStateException("route path is already owned by another page")
            }
        }
    }

    override fun saveSettings(settings: SiteSettingsWrite) {
        SiteSettingsTable.upsert(SiteSettingsTable.settingsDataSourceId) {
            it[settingsDataSourceId] = settings.settingsDataSourceId
            it[rootPageId] = settings.rootPageId.value
            it[headerPageId] = settings.headerPageId?.value
            it[footerPageId] = settings.footerPageId?.value
            it[headJson] = settings.headJson
            it[lastSyncedAt] = settings.syncedAt.offset()
            it[refreshAfter] = settings.refreshAfter.offset()
            it[lastError] = settings.lastError
            it[failureCount] = 0
        }
    }

    override fun makePagePrivate(pageId: NotionPageId, refreshAfter: Instant, lastError: String?) {
        NotionPageTable.update({ NotionPageTable.pageId eq pageId.value }) {
            it[publicUrl] = null
            it[visibility] = PageVisibility.PRIVATE.name
            it[NotionPageTable.refreshAfter] = refreshAfter.offset()
            it[NotionPageTable.lastError] = lastError
        }
        PageRouteTable.update({ PageRouteTable.pageId eq pageId.value }) {
            it[active] = false
        }
    }

    override fun touchPublicPage(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant) {
        NotionPageTable.update({ NotionPageTable.pageId eq pageId.value }) {
            it[lastSyncedAt] = syncedAt.offset()
            it[NotionPageTable.refreshAfter] = refreshAfter.offset()
            it[failureCount] = 0
            it[lastError] = null
        }
    }

    override fun pageFailureCount(pageId: NotionPageId): Int = NotionPageTable.selectAll()
        .where { NotionPageTable.pageId eq pageId.value }
        .singleOrNull()
        ?.get(NotionPageTable.failureCount) ?: 0

    override fun settingsFailureCount(settingsDataSourceId: String): Int = SiteSettingsTable.selectAll()
        .where { SiteSettingsTable.settingsDataSourceId eq settingsDataSourceId }
        .singleOrNull()
        ?.get(SiteSettingsTable.failureCount) ?: 0

    override fun recordPageFailure(pageId: NotionPageId, failureCount: Int, refreshAfter: Instant, lastError: String) {
        NotionPageTable.update({ NotionPageTable.pageId eq pageId.value }) {
            it[NotionPageTable.failureCount] = failureCount
            it[NotionPageTable.refreshAfter] = refreshAfter.offset()
            it[NotionPageTable.lastError] = lastError
        }
    }

    override fun recordSettingsFailure(settingsDataSourceId: String, failureCount: Int, refreshAfter: Instant, lastError: String) {
        val updated = SiteSettingsTable.update({ SiteSettingsTable.settingsDataSourceId eq settingsDataSourceId }) {
            it[SiteSettingsTable.failureCount] = failureCount
            it[SiteSettingsTable.refreshAfter] = refreshAfter.offset()
            it[SiteSettingsTable.lastError] = lastError
        }
        if (updated == 0) {
            SiteSettingsTable.insert {
                it[SiteSettingsTable.settingsDataSourceId] = settingsDataSourceId
                it[SiteSettingsTable.rootPageId] = null
                it[SiteSettingsTable.headJson] = "{}"
                it[SiteSettingsTable.lastSyncedAt] = null
                it[SiteSettingsTable.refreshAfter] = refreshAfter.offset()
                it[SiteSettingsTable.lastError] = lastError
                it[SiteSettingsTable.failureCount] = failureCount
            }
        }
    }

    private fun canonicalPathFor(pageId: NotionPageId): String? = PageRouteTable.selectAll()
        .where {
            (PageRouteTable.pageId eq pageId.value) and
                (PageRouteTable.kind eq PageRouteKind.CANONICAL.name) and
                (PageRouteTable.active eq true)
        }
        .singleOrNull()
        ?.get(PageRouteTable.path)

    private fun Instant.offset() = atOffset(ZoneOffset.UTC)
}
