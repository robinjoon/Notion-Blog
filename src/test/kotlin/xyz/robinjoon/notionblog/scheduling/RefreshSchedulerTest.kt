package xyz.robinjoon.notionblog.scheduling

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.`in`.SettingsRefreshResult
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.domain.model.NotionPageId

class RefreshSchedulerTest {
    @Test
    fun `passes only due ids to the same page and settings refresh use cases`() {
        val now = Instant.parse("2026-07-01T00:00:00Z")
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val persistence = mockk<BlogPersistencePort>()
        val pageRefresh = mockk<RefreshPageUseCase>()
        val settingsRefresh = mockk<RefreshSettingsUseCase>()
        every { persistence.findDueSettingsDataSourceIds(now, 50) } returns listOf("settings-db")
        every { persistence.findDuePageIds(now, 50) } returns listOf(pageId)
        every { persistence.hasSettings("settings-db") } returns true
        every { settingsRefresh.refresh() } returns SettingsRefreshResult(pageId, null, null, "{}")
        every { pageRefresh.refresh(pageId) } returns true

        RefreshScheduler(
            persistence,
            pageRefresh,
            mapOf("settings-db" to settingsRefresh),
            Clock.fixed(now, ZoneOffset.UTC),
        ).refreshDue()

        verify(exactly = 1) { settingsRefresh.refresh() }
        verify(exactly = 1) { pageRefresh.refresh(pageId) }
    }
}
