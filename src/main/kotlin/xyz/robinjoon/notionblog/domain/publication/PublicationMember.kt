package xyz.robinjoon.notionblog.domain.publication

import xyz.robinjoon.notionblog.domain.post.PostId

data class PublicationMember(
    val revisionId: PublicationRevisionId,
    val postId: PostId,
    val parentPostId: PostId?,
    val depth: Int,
) {
    init {
        require(depth >= 0) { "publication member depth cannot be negative" }
    }
}
