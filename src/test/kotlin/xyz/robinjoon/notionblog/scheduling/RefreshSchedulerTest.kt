package xyz.robinjoon.notionblog.scheduling

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.`in`.SettingsRefreshResult
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.domain.model.NotionPageId

@ExtendWith(OutputCaptureExtension::class)
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

    @Test
    fun `logs every target lookup failure safely and continues the scheduler tick`(output: CapturedOutput) {
        val now = Instant.parse("2026-07-01T00:00:00Z")
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val persistence = mockk<BlogPersistencePort>()
        val pageRefresh = mockk<RefreshPageUseCase>(relaxed = true)
        val settingsRefresh = mockk<RefreshSettingsUseCase>(relaxed = true)
        every { persistence.hasSettings("settings-db") } throws IllegalStateException("password=bootstrap-secret")
        every { persistence.findDueSettingsDataSourceIds(now, 50) } throws IllegalStateException("password=settings-secret")
        every { persistence.findDuePageIds(now, 50) } throws IllegalStateException("password=pages-secret")

        RefreshScheduler(
            persistence,
            pageRefresh,
            mapOf("settings-db" to settingsRefresh),
            Clock.fixed(now, ZoneOffset.UTC),
        ).refreshDue()

        val logs = output.out + output.err
        assertThat(logs)
            .contains("ERROR", "Refresh target lookup failed")
            .contains("operation=hasSettings", "operation=findDueSettings", "operation=findDuePages")
            .contains("errorType=IllegalStateException")
            .doesNotContain("bootstrap-secret", "settings-secret", "pages-secret")
        assertThat(Regex("operation=hasSettings").findAll(logs).count()).isEqualTo(1)
        assertThat(Regex("operation=findDueSettings").findAll(logs).count()).isEqualTo(1)
        assertThat(Regex("operation=findDuePages").findAll(logs).count()).isEqualTo(1)
        verify(exactly = 0) { settingsRefresh.refresh() }
        verify(exactly = 0) { pageRefresh.refresh(pageId) }
    }

    @Test
    fun `does not swallow fatal errors from refresh services`() {
        val now = Instant.parse("2026-07-01T00:00:00Z")
        val persistence = mockk<BlogPersistencePort>()
        val pageRefresh = mockk<RefreshPageUseCase>(relaxed = true)
        val settingsRefresh = mockk<RefreshSettingsUseCase>()
        every { persistence.hasSettings("settings-db") } returns false
        every { settingsRefresh.refresh() } throws AssertionError("fatal")
        val scheduler = RefreshScheduler(
            persistence,
            pageRefresh,
            mapOf("settings-db" to settingsRefresh),
            Clock.fixed(now, ZoneOffset.UTC),
        )

        assertThatThrownBy { scheduler.refreshDue() }.isInstanceOf(AssertionError::class.java)
    }
}
