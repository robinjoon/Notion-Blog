package xyz.robinjoon.notionblog.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import xyz.robinjoon.notionblog.adapter.input.scheduling.SynchronizationScheduler
import xyz.robinjoon.notionblog.application.service.SynchronizationQueryService
import xyz.robinjoon.notionblog.application.service.SynchronizePostService
import xyz.robinjoon.notionblog.application.service.SynchronizePublicationService
import xyz.robinjoon.notionblog.application.service.SynchronizeSiteConfigurationService
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "blog.synchronization",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun synchronizationScheduler(
        queryService: SynchronizationQueryService,
        siteConfigurationService: SynchronizeSiteConfigurationService,
        publicationService: SynchronizePublicationService,
        postService: SynchronizePostService,
        clock: Clock,
        properties: BlogProperties,
    ): SynchronizationScheduler = SynchronizationScheduler(
        queryService = queryService,
        siteConfigurationService = siteConfigurationService,
        publicationService = publicationService,
        postService = postService,
        clock = clock,
        dueBatchSize = properties.synchronization.dueBatchSize,
    )
}
