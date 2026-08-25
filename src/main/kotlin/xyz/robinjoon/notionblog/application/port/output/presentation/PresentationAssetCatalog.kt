package xyz.robinjoon.notionblog.application.port.output.presentation

import xyz.robinjoon.notionblog.application.model.PresentationAssetDescriptor
import xyz.robinjoon.notionblog.application.model.ResolvedPresentationAsset
import xyz.robinjoon.notionblog.domain.site.PresentationAssetRef

interface PresentationAssetCatalog {
    fun resolve(reference: PresentationAssetRef): PresentationAssetDescriptor?

    fun resolveCurrent(key: String): ResolvedPresentationAsset?
}
