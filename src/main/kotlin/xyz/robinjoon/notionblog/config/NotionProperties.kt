package xyz.robinjoon.notionblog.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("notion")
data class NotionProperties(
    val token: String,
    val settingsDataSourceId: String,
    val apiVersion: String,
    val baseUrl: String,
    val requestTimeout: Duration,
    val collectionTimeout: Duration,
) {
    init {
        require(token.isNotBlank()) { "notion.token must not be blank" }
        require(settingsDataSourceId.isNotBlank()) { "notion.settings-data-source-id must not be blank" }
        require(apiVersion.isNotBlank()) { "notion.api-version must not be blank" }
        require(!requestTimeout.isZero && !requestTimeout.isNegative) { "notion.request-timeout must be positive" }
        require(!collectionTimeout.isZero && !collectionTimeout.isNegative) { "notion.collection-timeout must be positive" }
    }
}
