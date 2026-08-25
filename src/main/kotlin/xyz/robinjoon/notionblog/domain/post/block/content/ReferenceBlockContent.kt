package xyz.robinjoon.notionblog.domain.post.block.content

import xyz.robinjoon.notionblog.domain.post.block.inline.LinkTarget
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

sealed interface ReferenceBlockContent : BlockContent {
    data class ChildPost(
        val title: String,
        val reference: SourceDocumentRef,
    ) : ReferenceBlockContent {
        init {
            require(title.isNotBlank()) { "child post title must not be blank" }
        }
    }

    data class DocumentLink(
        val reference: SourceDocumentRef,
        val originalUrl: java.net.URI?,
    ) : ReferenceBlockContent

    data class DatabaseLink(
        val reference: SourceDocumentRef,
        val originalUrl: java.net.URI?,
    ) : ReferenceBlockContent

    data class Breadcrumb(
        val items: List<LinkTarget>,
    ) : ReferenceBlockContent

    data object TableOfContents : ReferenceBlockContent
}
