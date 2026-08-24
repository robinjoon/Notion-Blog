package xyz.robinjoon.notionblog.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.`in`.RefreshSettingsUseCase
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.scheduling.RefreshScheduler
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "blog.refresh", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class RefreshSchedulingConfiguration {
    @Bean
    fun refreshScheduler(
        persistence: BlogPersistencePort,
        pageRefresh: RefreshPageUseCase,
        settingsRefresh: RefreshSettingsUseCase,
        notion: NotionProperties,
        clock: Clock,
    ): RefreshScheduler = RefreshScheduler(
        persistence,
        pageRefresh,
        mapOf(notion.settingsDataSourceId to settingsRefresh),
        clock,
    )
}
