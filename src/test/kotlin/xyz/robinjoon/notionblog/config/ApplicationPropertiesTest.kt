package xyz.robinjoon.notionblog.config

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ApplicationPropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `binds Notion and blog runtime settings to typed properties`() {
        contextRunner
            .withPropertyValues(
                "notion.token=test-token",
                "notion.settings-data-source-id=settings-data-source",
                "notion.api-version=2025-09-03",
                "notion.base-url=https://api.notion.test/v1",
                "notion.request-timeout=4s",
                "notion.collection-timeout=20s",
                "blog.base-url=https://blog.example.com",
                "blog.refresh.enabled=false",
                "blog.refresh.interval-ms=45000",
                "blog.refresh.thread-count=3",
                "blog.refresh.queue-capacity=24",
            )
            .run { context ->
                assertThat(context).hasNotFailed()

                val notion = context.getBean(NotionProperties::class.java)
                assertThat(notion.token).isEqualTo("test-token")
                assertThat(notion.settingsDataSourceId).isEqualTo("settings-data-source")
                assertThat(notion.apiVersion).isEqualTo("2025-09-03")
                assertThat(notion.baseUrl).isEqualTo("https://api.notion.test/v1")
                assertThat(notion.requestTimeout).isEqualTo(Duration.ofSeconds(4))
                assertThat(notion.collectionTimeout).isEqualTo(Duration.ofSeconds(20))

                val blog = context.getBean(BlogProperties::class.java)
                assertThat(blog.baseUrl.toString()).isEqualTo("https://blog.example.com")
                assertThat(blog.refresh.enabled).isFalse()
                assertThat(blog.refresh.intervalMs).isEqualTo(45_000)
                assertThat(blog.refresh.threadCount).isEqualTo(3)
                assertThat(blog.refresh.queueCapacity).isEqualTo(24)
            }
    }

    @Test
    fun `does not provide a default Notion token`() {
        contextRunner
            .withPropertyValues(
                "notion.settings-data-source-id=settings-data-source",
                "notion.api-version=2025-09-03",
                "notion.base-url=https://api.notion.test/v1",
                "notion.request-timeout=4s",
                "notion.collection-timeout=20s",
                "blog.base-url=https://blog.example.com",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasMessageContaining("notion")
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotionProperties::class, BlogProperties::class)
    private class PropertiesConfiguration
}
