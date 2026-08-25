package xyz.robinjoon.notionblog.domain.post.block.content

import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

sealed interface ReusableBlockContent : BlockContent {
    data class Synchronized(
        val origin: SynchronizedBlockOrigin?,
    ) : ReusableBlockContent

    data object Template : ReusableBlockContent
}

data class SynchronizedBlockOrigin(
    val document: SourceDocumentRef,
    val blockExternalId: String,
) {
    init {
        require(blockExternalId.isNotBlank()) { "synchronized block origin id must not be blank" }
    }
}
