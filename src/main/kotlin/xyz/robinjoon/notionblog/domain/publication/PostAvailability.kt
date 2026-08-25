package xyz.robinjoon.notionblog.domain.publication

import xyz.robinjoon.notionblog.domain.post.PostId
import java.time.Instant

data class PostAvailability(
    val postId: PostId,
    val status: PostAvailabilityStatus,
    val confirmedAt: Instant,
)

enum class PostAvailabilityStatus {
    PUBLISHED,
    UNPUBLISHED,
}
