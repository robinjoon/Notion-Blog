package xyz.robinjoon.notionblog.domain.source

import xyz.robinjoon.notionblog.domain.post.PostId

data class PostSourceBinding(
    val postId: PostId,
    val sourceDocument: SourceDocumentRef,
)
