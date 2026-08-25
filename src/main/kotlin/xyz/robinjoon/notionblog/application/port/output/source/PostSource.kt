package xyz.robinjoon.notionblog.application.port.output.source

import xyz.robinjoon.notionblog.application.model.ImportedPost
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef

interface PostSource {
    fun fetch(reference: SourceDocumentRef): ImportedPost
}
