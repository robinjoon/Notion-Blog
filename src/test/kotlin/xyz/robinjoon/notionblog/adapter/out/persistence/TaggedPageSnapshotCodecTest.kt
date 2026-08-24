package xyz.robinjoon.notionblog.adapter.out.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.domain.model.BookmarkBlock
import xyz.robinjoon.notionblog.domain.model.BulletedListItemBlock
import xyz.robinjoon.notionblog.domain.model.CalloutBlock
import xyz.robinjoon.notionblog.domain.model.ChildPageBlock
import xyz.robinjoon.notionblog.domain.model.CodeBlock
import xyz.robinjoon.notionblog.domain.model.ColumnBlock
import xyz.robinjoon.notionblog.domain.model.DividerBlock
import xyz.robinjoon.notionblog.domain.model.FileBlock
import xyz.robinjoon.notionblog.domain.model.HeadingBlock
import xyz.robinjoon.notionblog.domain.model.HeadingLevel
import xyz.robinjoon.notionblog.domain.model.ImageBlock
import xyz.robinjoon.notionblog.domain.model.NotionBlock
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NumberedListItemBlock
import xyz.robinjoon.notionblog.domain.model.ParagraphBlock
import xyz.robinjoon.notionblog.domain.model.QuoteBlock
import xyz.robinjoon.notionblog.domain.model.RichText
import xyz.robinjoon.notionblog.domain.model.TableBlock
import xyz.robinjoon.notionblog.domain.model.TableRowBlock
import xyz.robinjoon.notionblog.domain.model.ToDoBlock
import xyz.robinjoon.notionblog.domain.model.ToggleBlock
import xyz.robinjoon.notionblog.domain.model.UnsupportedBlock
import xyz.robinjoon.notionblog.domain.model.VideoBlock

class TaggedPageSnapshotCodecTest {
    @Test
    fun `round trips every normalized block subtype with explicit type tags`() {
        val text = listOf(RichText("text"))
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val blocks: List<NotionBlock> = listOf(
            ParagraphBlock("1", text), HeadingBlock("2", HeadingLevel.ONE, text), BulletedListItemBlock("3", text),
            NumberedListItemBlock("4", text), ToDoBlock("5", text, true), ToggleBlock("6", text), QuoteBlock("7", text),
            CalloutBlock("8", text, "💡"), DividerBlock("9"), CodeBlock("10", text, "kotlin"),
            ImageBlock("11", "https://example.com/image"), VideoBlock("12", "https://example.com/video"),
            FileBlock("13", "https://example.com/file", "file"), BookmarkBlock("14", "https://example.com/bookmark"),
            TableBlock("15", 1), TableRowBlock("16", listOf(text)), ColumnBlock("17"), ChildPageBlock("18", "Child", pageId),
            UnsupportedBlock("19", "unsupported"),
        )
        val codec = TaggedPageSnapshotCodec()

        val encoded = codec.encode(blocks)

        assertThat(encoded).contains("\"type\"")
        assertThat(codec.decode(encoded)).containsExactlyElementsOf(blocks)
    }
}
