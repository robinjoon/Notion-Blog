package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.site.PresentationAssetRef

data class ResolvedPresentationAsset(
    val reference: PresentationAssetRef,
    val descriptor: PresentationAssetDescriptor,
)
