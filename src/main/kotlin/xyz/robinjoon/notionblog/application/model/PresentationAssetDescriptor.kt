package xyz.robinjoon.notionblog.application.model

data class PresentationAssetDescriptor(
    val publicPath: String,
    val mediaType: String,
    val integrity: String,
)
