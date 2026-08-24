package xyz.robinjoon.notionblog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.servlet.DispatcherServlet
import xyz.robinjoon.notionblog.adapter.out.notion.NotionRestClientAdapter
import xyz.robinjoon.notionblog.adapter.out.persistence.ExposedBlogPersistenceAdapter
import xyz.robinjoon.notionblog.adapter.out.persistence.TaggedPageSnapshotCodec
import xyz.robinjoon.notionblog.application.service.AsyncPageRefreshRequester
import xyz.robinjoon.notionblog.application.service.PageAccessService
import xyz.robinjoon.notionblog.application.service.PageRefreshService
import xyz.robinjoon.notionblog.application.service.SettingsRefreshService
import xyz.robinjoon.notionblog.application.service.TransactionalPageStore
import xyz.robinjoon.notionblog.application.service.TransactionalSettingsStore
import xyz.robinjoon.notionblog.scheduling.RefreshScheduler
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.ThreadPoolExecutor

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:1/notion_blog",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=false",
        "notion.token=test-token",
        "notion.settings-data-source-id=settings-data-source",
        "notion.api-version=2025-09-03",
        "notion.base-url=http://127.0.0.1:1/v1",
        "notion.request-timeout=1s",
        "notion.collection-timeout=2s",
        "blog.base-url=https://blog.example.com",
        "blog.refresh.enabled=false",
        "blog.refresh.interval-ms=60000",
        "blog.refresh.thread-count=2",
        "blog.refresh.queue-capacity=16",
    ],
)
class ContextLoadTest(
    @Autowired private val applicationContext: ApplicationContext,
) {
    @Test
    fun `MVC context starts with the complete application bean graph without remote calls`() {
        assertThat(applicationContext.getBean(DispatcherServlet::class.java)).isNotNull
        val transactionManager = applicationContext.getBean(PlatformTransactionManager::class.java)
        assertThat(transactionManager).isNotInstanceOf(JdbcTransactionManager::class.java)
        assertThat(transactionManager.javaClass.name).contains("org.jetbrains.exposed")
        assertThat(applicationContext.getBean(HealthEndpoint::class.java)).isNotNull
        assertThat(applicationContext.getBean(Clock::class.java).zone).isEqualTo(ZoneOffset.UTC)

        assertThat(applicationContext.getBean(ExposedBlogPersistenceAdapter::class.java)).isNotNull
        assertThat(applicationContext.getBean(TaggedPageSnapshotCodec::class.java)).isNotNull
        assertThat(applicationContext.getBean(NotionRestClientAdapter::class.java)).isNotNull
        assertThat(applicationContext.getBean(TransactionalPageStore::class.java)).isNotNull
        assertThat(applicationContext.getBean(TransactionalSettingsStore::class.java)).isNotNull
        assertThat(applicationContext.getBean(PageRefreshService::class.java)).isNotNull
        assertThat(applicationContext.getBean(SettingsRefreshService::class.java)).isNotNull
        assertThat(applicationContext.getBean(AsyncPageRefreshRequester::class.java)).isNotNull
        assertThat(applicationContext.getBean(PageAccessService::class.java)).isNotNull

        val executor = applicationContext.getBean("pageRefreshExecutor", ThreadPoolExecutor::class.java)
        assertThat(executor.corePoolSize).isEqualTo(2)
        assertThat(executor.maximumPoolSize).isEqualTo(2)
        assertThat(executor.queue.remainingCapacity()).isEqualTo(16)
        assertThat(applicationContext.getBeansOfType(RefreshScheduler::class.java)).isEmpty()
    }
}
