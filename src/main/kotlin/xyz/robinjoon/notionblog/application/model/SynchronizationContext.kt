package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.post.PostId
import xyz.robinjoon.notionblog.domain.publication.PublicationId
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

data class PublicationSynchronizationContext(
    val publicationId: PublicationId,
    val rootDocument: SourceDocumentRef,
)

data class PostSynchronizationContext(
    val publicationId: PublicationId,
    val postId: PostId,
    val sourceDocument: SourceDocumentRef,
    val activeDirectChildren: Set<SourceDocumentRef>,
)
