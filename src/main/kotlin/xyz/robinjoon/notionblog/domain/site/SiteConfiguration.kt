package xyz.robinjoon.notionblog.domain.site

import xyz.robinjoon.notionblog.domain.publication.PublicationId
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

data class SiteConfiguration(
    val publicationId: PublicationId,
    val rootDocument: SourceDocumentRef,
    val headerDocument: SourceDocumentRef?,
    val footerDocument: SourceDocumentRef?,
    val metadata: SiteMetadata,
    val presentationProfile: PresentationProfileRef,
)

data class SiteMetadata(
    val siteName: String,
    val defaultDescription: String?,
    val languageTag: String,
    val favicon: PresentationAssetRef?,
) {
    init {
        require(siteName.isNotBlank()) { "site name must not be blank" }
        require(defaultDescription == null || defaultDescription.isNotBlank()) {
            "default description must not be blank when present"
        }
        require(languageTag.isNotBlank()) { "language tag must not be blank" }
    }
}
