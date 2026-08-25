package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.site.PresentationProfileKey
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

data class ImportedSiteConfiguration(
    val rootDocument: SourceDocumentRef,
    val headerDocument: SourceDocumentRef?,
    val footerDocument: SourceDocumentRef?,
    val metadata: ImportedSiteMetadata,
    val presentationProfileKey: PresentationProfileKey?,
)

data class ImportedSiteMetadata(
    val siteName: String,
    val defaultDescription: String?,
    val languageTag: String,
    val faviconAssetKey: String?,
)
