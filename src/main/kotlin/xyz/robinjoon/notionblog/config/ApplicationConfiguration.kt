package xyz.robinjoon.notionblog.config

import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.notionblog.adapter.out.notion.NotionRestClientAdapter
import xyz.robinjoon.notionblog.adapter.out.persistence.ExposedBlogPersistenceAdapter
import xyz.robinjoon.notionblog.adapter.out.persistence.TaggedPageSnapshotCodec
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.out.notion.NotionGateway
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.application.service.AsyncPageRefreshRequester
import xyz.robinjoon.notionblog.application.service.PageAccessService
import xyz.robinjoon.notionblog.application.service.PageRefreshRequester
import xyz.robinjoon.notionblog.application.service.PageRefreshService
import xyz.robinjoon.notionblog.application.service.SettingsRefreshService
import xyz.robinjoon.notionblog.application.service.TransactionalPageStore
import xyz.robinjoon.notionblog.application.service.TransactionalSettingsStore

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotionProperties::class, BlogProperties::class)
class ApplicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun blogPersistence(): ExposedBlogPersistenceAdapter = ExposedBlogPersistenceAdapter()

    @Bean
    fun pageSnapshotCodec(): TaggedPageSnapshotCodec = TaggedPageSnapshotCodec()

    @Bean
    fun notionGateway(properties: NotionProperties): NotionRestClientAdapter =
        NotionRestClientAdapter(
            baseUrl = properties.baseUrl,
            token = properties.token,
            apiVersion = properties.apiVersion,
            requestTimeout = properties.requestTimeout,
            totalCollectionTimeout = properties.collectionTimeout,
        )

    @Bean
    fun transactionalPageStore(persistence: BlogPersistencePort): TransactionalPageStore =
        TransactionalPageStore(persistence)

    @Bean
    fun transactionalSettingsStore(persistence: BlogPersistencePort): TransactionalSettingsStore =
        TransactionalSettingsStore(persistence)

    @Bean
    fun pageRefreshService(
        gateway: NotionGateway,
        persistence: BlogPersistencePort,
        store: TransactionalPageStore,
        clock: Clock,
        snapshotCodec: PageSnapshotCodec,
    ): PageRefreshService = PageRefreshService(gateway, persistence, store, clock, snapshotCodec)

    @Bean
    fun settingsRefreshService(
        gateway: NotionGateway,
        persistence: BlogPersistencePort,
        properties: NotionProperties,
        clock: Clock,
        store: TransactionalSettingsStore,
    ): SettingsRefreshService =
        SettingsRefreshService(gateway, persistence, properties.settingsDataSourceId, clock, store)

    @Bean(destroyMethod = "shutdown")
    fun pageRefreshExecutor(properties: BlogProperties): ThreadPoolExecutor {
        val threadCount = properties.refresh.threadCount
        return ThreadPoolExecutor(
            threadCount,
            threadCount,
            0,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(properties.refresh.queueCapacity),
            PageRefreshThreadFactory(),
            ThreadPoolExecutor.DiscardPolicy(),
        )
    }

    @Bean
    fun pageRefreshRequester(
        refresh: RefreshPageUseCase,
        @Qualifier("pageRefreshExecutor") executor: ThreadPoolExecutor,
    ): AsyncPageRefreshRequester = AsyncPageRefreshRequester(refresh, executor)

    @Bean
    fun pageAccessService(
        persistence: BlogPersistencePort,
        pageRefresh: PageRefreshRequester,
        clock: Clock,
        snapshotCodec: PageSnapshotCodec,
    ): PageAccessService = PageAccessService(persistence, pageRefresh, clock, snapshotCodec)
}

private class PageRefreshThreadFactory : ThreadFactory {
    private val sequence = AtomicInteger()

    override fun newThread(task: Runnable): Thread =
        Thread(task, "page-refresh-${sequence.incrementAndGet()}").apply { isDaemon = true }
}
