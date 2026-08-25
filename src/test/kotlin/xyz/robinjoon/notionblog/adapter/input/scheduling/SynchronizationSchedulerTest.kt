package xyz.robinjoon.notionblog.adapter.input.scheduling

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.notionblog.application.service.SynchronizationQueryService
import xyz.robinjoon.notionblog.application.service.SynchronizePostService
import xyz.robinjoon.notionblog.application.service.SynchronizePublicationService
import xyz.robinjoon.notionblog.application.service.SynchronizeSiteConfigurationService
import xyz.robinjoon.notionblog.domain.post.PostId
import xyz.robinjoon.notionblog.domain.publication.PublicationId
import xyz.robinjoon.notionblog.domain.sync.SyncTarget
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@ExtendWith(OutputCaptureExtension::class)
class SynchronizationSchedulerTest {
    private val now = Instant.parse("2026-08-25T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val queryService = mockk<SynchronizationQueryService>()
    private val siteConfigurationService = mockk<SynchronizeSiteConfigurationService>()
    private val publicationService = mockk<SynchronizePublicationService>()
    private val postService = mockk<SynchronizePostService>()

    @Test
    fun `passes injected now and positive batch limit to due target query`() {
        every { queryService.findDueTargets(now, 7) } returns emptyList()

        scheduler(dueBatchSize = 7).synchronizeDue()

        verify(exactly = 1) { queryService.findDueTargets(now, 7) }
    }

    @Test
    fun `dispatches every target in query order`() {
        val publicationId = PublicationId(UUID.randomUUID())
        val postId = PostId(UUID.randomUUID())
        val targets = listOf(
            SyncTarget.SiteConfiguration,
            SyncTarget.Publication(publicationId),
            SyncTarget.Post(postId),
        )
        val events = mutableListOf<String>()
        every { queryService.findDueTargets(now, 7) } returns targets
        every { siteConfigurationService.synchronize() } answers { events += "site" }
        every { publicationService.synchronize() } answers { events += "publication" }
        every { postService.synchronize(postId) } answers { events += "post" }

        scheduler(dueBatchSize = 7).synchronizeDue()

        assertThat(events).containsExactly("site", "publication", "post")
    }

    @Test
    fun `isolates a runtime failure to its target and does not log exception message`(output: CapturedOutput) {
        val publicationId = PublicationId(UUID.randomUUID())
        val postId = PostId(UUID.randomUUID())
        every { queryService.findDueTargets(now, 7) } returns listOf(
            SyncTarget.SiteConfiguration,
            SyncTarget.Publication(publicationId),
            SyncTarget.Post(postId),
        )
        every { siteConfigurationService.synchronize() } throws RuntimeException("token=do-not-log")
        every { publicationService.synchronize() } just Runs
        every { postService.synchronize(postId) } just Runs

        scheduler(dueBatchSize = 7).synchronizeDue()

        verify(exactly = 1) { publicationService.synchronize() }
        verify(exactly = 1) { postService.synchronize(postId) }
        val logs = output.out + output.err
        assertThat(logs)
            .contains("Synchronization target failed", "targetKind=SITE_CONFIGURATION", "errorType=RuntimeException")
            .doesNotContain("token=do-not-log")
    }

    @Test
    fun `does nothing when no targets are due`() {
        every { queryService.findDueTargets(now, 7) } returns emptyList()

        scheduler(dueBatchSize = 7).synchronizeDue()

        verify(exactly = 0) { siteConfigurationService.synchronize() }
        verify(exactly = 0) { publicationService.synchronize() }
        verify(exactly = 0) { postService.synchronize(any()) }
    }

    @Test
    fun `skips reentrant invocation while the outer invocation is running`() {
        lateinit var instance: SynchronizationScheduler
        every { queryService.findDueTargets(now, 7) } returns listOf(SyncTarget.SiteConfiguration)
        every { siteConfigurationService.synchronize() } answers {
            instance.synchronizeDue()
        }
        instance = scheduler(dueBatchSize = 7)

        instance.synchronizeDue()

        verify(exactly = 1) { queryService.findDueTargets(now, 7) }
        verify(exactly = 1) { siteConfigurationService.synchronize() }
    }

    @Test
    fun `skips concurrent invocation while another invocation is running`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { queryService.findDueTargets(now, 7) } returns listOf(SyncTarget.SiteConfiguration)
        every { siteConfigurationService.synchronize() } answers {
            entered.countDown()
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
        }
        val scheduler = scheduler(dueBatchSize = 7)
        val first = thread(start = true) { scheduler.synchronizeDue() }

        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        scheduler.synchronizeDue()
        release.countDown()
        first.join(5_000)

        assertThat(first.isAlive).isFalse()
        verify(exactly = 1) { queryService.findDueTargets(now, 7) }
        verify(exactly = 1) { siteConfigurationService.synchronize() }
    }

    @Test
    fun `requires a positive due batch size`() {
        assertThatThrownBy { scheduler(dueBatchSize = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `has a scheduled method without a transaction boundary`() {
        val method = SynchronizationScheduler::class.java.getDeclaredMethod("synchronizeDue")

        assertThat(method.isAnnotationPresent(Scheduled::class.java)).isTrue()
        assertThat(method.isAnnotationPresent(Transactional::class.java)).isFalse()
        assertThat(SynchronizationScheduler::class.java.isAnnotationPresent(Transactional::class.java)).isFalse()
    }

    @Test
    fun `is enabled by a target property when it is wired as a bean`() {
        val condition = SynchronizationScheduler::class.java.getAnnotation(ConditionalOnProperty::class.java)

        assertThat(condition).isNotNull
        assertThat(condition.prefix).isEqualTo("blog.synchronization")
        assertThat(condition.name).containsExactly("enabled")
    }

    private fun scheduler(dueBatchSize: Int) = SynchronizationScheduler(
        queryService = queryService,
        siteConfigurationService = siteConfigurationService,
        publicationService = publicationService,
        postService = postService,
        clock = clock,
        dueBatchSize = dueBatchSize,
    )
}
