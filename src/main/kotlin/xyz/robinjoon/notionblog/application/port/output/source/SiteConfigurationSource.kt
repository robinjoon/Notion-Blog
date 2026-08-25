package xyz.robinjoon.notionblog.application.port.output.source

import xyz.robinjoon.notionblog.application.model.ImportedSiteConfiguration

interface SiteConfigurationSource {
    fun fetch(): ImportedSiteConfiguration
}
