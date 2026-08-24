package xyz.robinjoon.notionblog.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("blog")
data class BlogProperties(
    val baseUrl: URI,
    val refresh: Refresh = Refresh(),
) {
    data class Refresh(
        val enabled: Boolean = true,
        val intervalMs: Long = 60_000,
        val threadCount: Int = 2,
        val queueCapacity: Int = 32,
    ) {
        init {
            require(intervalMs > 0) { "blog.refresh.interval-ms must be positive" }
            require(threadCount in 1..8) { "blog.refresh.thread-count must be between 1 and 8" }
            require(queueCapacity in 1..1024) { "blog.refresh.queue-capacity must be between 1 and 1024" }
        }
    }
}
