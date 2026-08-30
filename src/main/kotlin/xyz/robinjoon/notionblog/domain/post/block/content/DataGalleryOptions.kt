package xyz.robinjoon.notionblog.domain.post.block.content

data class DataGalleryOptions(
    val size: DataCardSize = DataCardSize.MEDIUM,
    val aspect: DataCoverAspect = DataCoverAspect.COVER,
    val layout: DataCardLayout = DataCardLayout.LIST,
)

enum class DataCardSize {
    SMALL,
    MEDIUM,
    LARGE,
}

enum class DataCoverAspect {
    CONTAIN,
    COVER,
}

enum class DataCardLayout {
    LIST,
    COMPACT,
}
