package xyz.robinjoon.notionblog.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class NotionBlockTest {
    @Test
    fun `retains normalized rich text and nested children without Notion DTOs`() {
        val block = ParagraphBlock(
            id = "block-1",
            richText = listOf(RichText("Read "), RichText("this", annotations = RichTextAnnotations(bold = true), link = "https://example.com")),
            children = listOf(QuoteBlock("block-2", listOf(RichText("Nested quote"))))
        )

        assertThat(block.richText.map(RichText::plainText)).containsExactly("Read ", "this")
        assertThat(block.children).containsExactly(QuoteBlock("block-2", listOf(RichText("Nested quote"))))
    }

    @Test
    fun `retains unknown blocks as safe typed fallbacks`() {
        assertThat(UnsupportedBlock("block-1", "synced_database").type).isEqualTo("synced_database")
    }

    @Test
    fun `requires tables to use a positive declared width`() {
        assertThatIllegalArgumentException().isThrownBy {
            TableBlock(id = "table-1", width = 0)
        }.withMessage("table width must be positive")
    }
}
