package xyz.robinjoon.notionblog.application.model

import xyz.robinjoon.notionblog.domain.post.Post
import xyz.robinjoon.notionblog.domain.source.SourceRevision
import java.time.Instant

data class StoredPost(
    val post: Post,
    val sourceRevision: SourceRevision,
    val capturedAt: Instant,
)
