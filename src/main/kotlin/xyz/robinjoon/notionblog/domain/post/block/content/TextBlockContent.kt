package xyz.robinjoon.notionblog.domain.post.block.content

import xyz.robinjoon.notionblog.domain.post.block.inline.InlineContent
import xyz.robinjoon.notionblog.domain.post.block.media.MediaSource

sealed interface TextBlockContent : BlockContent {
    val richText: List<InlineContent>

    data class Paragraph(
        override val richText: List<InlineContent>,
    ) : TextBlockContent

    data class Heading(
        val level: HeadingLevel,
        override val richText: List<InlineContent>,
        val isToggleable: Boolean = false,
    ) : TextBlockContent

    data class Quote(
        override val richText: List<InlineContent>,
    ) : TextBlockContent

    data class Toggle(
        override val richText: List<InlineContent>,
    ) : TextBlockContent

    data class Callout(
        override val richText: List<InlineContent>,
        val icon: BlockIcon?,
    ) : TextBlockContent

    data class Code(
        override val richText: List<InlineContent>,
        val language: String,
        val caption: List<InlineContent> = emptyList(),
    ) : TextBlockContent {
        init {
            require(language.isNotBlank()) { "code language must not be blank" }
        }
    }

    data class Equation(
        val expression: String,
    ) : TextBlockContent {
        override val richText: List<InlineContent> = emptyList()

        init {
            require(expression.isNotBlank()) { "equation expression must not be blank" }
        }
    }
}

enum class HeadingLevel {
    ONE,
    TWO,
    THREE,
    FOUR,
}

sealed interface BlockIcon {
    data class Emoji(val value: String) : BlockIcon {
        init {
            require(value.isNotBlank()) { "emoji icon must not be blank" }
        }
    }

    data class Media(val source: MediaSource) : BlockIcon
}
