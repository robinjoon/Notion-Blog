package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.site.SiteConfiguration

data class AppliedSiteConfiguration(
    val configuration: SiteConfiguration,
    val rootChanged: Boolean,
)
