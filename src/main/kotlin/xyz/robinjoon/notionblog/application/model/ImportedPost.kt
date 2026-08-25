package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.post.block.BlockTree
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef
import xyz.robinjoon.notionblog.domain.source.SourceRevision

data class ImportedPost(
    val sourceDocument: SourceDocumentRef,
    val title: String,
    val publicationStatus: ImportedPublicationStatus,
    val sourceRevision: SourceRevision,
    val content: BlockTree,
    val containedChildren: List<SourceDocumentRef>,
)

enum class ImportedPublicationStatus {
    PUBLISHED,
    UNPUBLISHED,
}
