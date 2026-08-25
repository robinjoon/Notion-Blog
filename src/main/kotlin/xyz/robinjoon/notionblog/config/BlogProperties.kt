package xyz.robinjoon.notionblog.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("blog")
data class BlogProperties(
    val synchronization: Synchronization = Synchronization(),
    val presentation: Presentation = Presentation(),
) {
    data class Synchronization(
        val enabled: Boolean = true,
        val intervalMs: Long = 60_000,
        val dueBatchSize: Int = 100,
        val successInterval: Duration = Duration.ofMinutes(5),
        val initialFailureDelay: Duration = Duration.ofSeconds(5),
        val maximumFailureDelay: Duration = Duration.ofMinutes(5),
    ) {
        init {
            require(intervalMs > 0) { "blog.synchronization.interval-ms must be positive" }
            require(dueBatchSize > 0) { "blog.synchronization.due-batch-size must be positive" }
            require(successInterval.isPositive) { "blog.synchronization.success-interval must be positive" }
            require(initialFailureDelay.isPositive) { "blog.synchronization.initial-failure-delay must be positive" }
            require(maximumFailureDelay >= initialFailureDelay) {
                "blog.synchronization.maximum-failure-delay must not be shorter than the initial failure delay"
            }
        }
    }

    data class Presentation(
        val defaultProfileKey: String = "notion-default",
        val assets: List<Asset> = emptyList(),
    ) {
        init {
            require(defaultProfileKey.isNotBlank()) { "blog.presentation.default-profile-key must not be blank" }
            val duplicateReferences = assets.groupBy { asset -> asset.referenceKey }.filterValues { it.size > 1 }
            require(duplicateReferences.isEmpty()) {
                "blog.presentation.assets must not contain duplicate key and version references"
            }
            val duplicateCurrentKeys = assets.filter(Asset::current).groupBy(Asset::key).filterValues { it.size > 1 }
            require(duplicateCurrentKeys.isEmpty()) {
                "blog.presentation.assets must not have more than one current asset for a key"
            }
        }
    }

    data class Asset(
        val key: String,
        val version: Long,
        val integrity: String,
        val publicPath: String,
        val mediaType: String,
        val current: Boolean,
    ) {
        init {
            require(key.isNotBlank()) { "blog.presentation.assets key must not be blank" }
            require(version > 0) { "blog.presentation.assets version must be positive" }
            require(integrity.isNotBlank()) { "blog.presentation.assets integrity must not be blank" }
            require(publicPath.isNotBlank()) { "blog.presentation.assets public-path must not be blank" }
            require(mediaType.isNotBlank()) { "blog.presentation.assets media-type must not be blank" }
        }

        internal val referenceKey: Pair<String, Long>
            get() = key to version
    }
}
