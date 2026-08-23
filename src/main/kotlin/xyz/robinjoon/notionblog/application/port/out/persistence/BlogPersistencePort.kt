package xyz.robinjoon.notionblog.application.port.out.persistence

import java.time.Instant
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.PageVisibility

interface BlogPersistencePort {
    fun resolveRoute(path: String): ResolvedRoute?

    fun findPublicPageSnapshot(pageId: NotionPageId): PublicPageSnapshot?

    fun findDuePageIds(now: Instant, limit: Int): List<NotionPageId>

    fun findDueSettingsDataSourceIds(now: Instant, limit: Int): List<String>

    fun hasSettings(settingsDataSourceId: String): Boolean = false

    fun replaceRootRoute(pageId: NotionPageId, changedAt: Instant) = Unit

    fun findRoutesForPage(pageId: NotionPageId): List<PageRoute> = emptyList()

    fun pathExists(path: String): Boolean = false

    fun isKnownPage(pageId: NotionPageId): Boolean = false

    fun isRootPage(pageId: NotionPageId): Boolean = false

    fun recordDiscoveredPage(pageId: NotionPageId, refreshAfter: Instant)

    fun savePublicPageSnapshot(snapshot: PublicPageSnapshotWrite)

    fun saveSettings(settings: SiteSettingsWrite)

    fun makePagePrivate(pageId: NotionPageId, refreshAfter: Instant, lastError: String? = null)

    fun touchPublicPage(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant)

    fun pageFailureCount(pageId: NotionPageId): Int = 0

    fun settingsFailureCount(settingsDataSourceId: String): Int = 0

    fun recordPageFailure(pageId: NotionPageId, failureCount: Int, refreshAfter: Instant, lastError: String) = Unit

    fun recordSettingsFailure(settingsDataSourceId: String, failureCount: Int, refreshAfter: Instant, lastError: String) = Unit
}

sealed interface ResolvedRoute {
    data class Page(val pageId: NotionPageId, val path: String) : ResolvedRoute

    data class Redirect(val destination: String) : ResolvedRoute
}

data class PublicPageSnapshot(
    val pageId: NotionPageId,
    val title: String,
    val snapshotJson: String,
    val notionLastEditedAt: Instant,
    val capturedAt: Instant,
    val refreshAfter: Instant,
)

data class PublicPageSnapshotWrite(
    val pageId: NotionPageId,
    val title: String,
    val notionUrl: String,
    val publicUrl: String,
    val notionLastEditedAt: Instant,
    val syncedAt: Instant,
    val refreshAfter: Instant,
    val snapshotJson: String,
    val capturedAt: Instant,
    val routes: List<PageRoute>,
)

data class SiteSettingsWrite(
    val settingsDataSourceId: String,
    val rootPageId: NotionPageId,
    val headerPageId: NotionPageId? = null,
    val footerPageId: NotionPageId? = null,
    val headJson: String,
    val syncedAt: Instant,
    val refreshAfter: Instant,
    val lastError: String? = null,
)
