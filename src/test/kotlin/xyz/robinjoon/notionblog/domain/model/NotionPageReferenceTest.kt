package xyz.robinjoon.notionblog.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotionPageReferenceTest {
    @Test
    fun `normalizes raw hyphenated Notion page ids`() {
        assertThat(NotionPageReference.parse("01234567-89AB-cdef-0123-456789abcdef"))
            .isEqualTo(NotionPageId("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `extracts a page id only from supported Notion hosts`() {
        assertThat(NotionPageReference.parse("https://workspace.notion.site/My-page-0123456789abcdef0123456789abcdef"))
            .isEqualTo(NotionPageId("0123456789abcdef0123456789abcdef"))
        assertThat(NotionPageReference.parse("https://app.notion.com/My-page-0123456789abcdef0123456789abcdef"))
            .isEqualTo(NotionPageId("0123456789abcdef0123456789abcdef"))
        assertThat(NotionPageReference.parse("https://notion.com/My-page-0123456789abcdef0123456789abcdef"))
            .isEqualTo(NotionPageId("0123456789abcdef0123456789abcdef"))
        assertThat(NotionPageReference.parse("https://example.com/page-0123456789abcdef0123456789abcdef")).isNull()
    }

    @Test
    fun `rejects lookalike hosts that merely contain a Notion domain`() {
        assertThat(NotionPageReference.parse("https://app.notion.com.attacker.example/page-0123456789abcdef0123456789abcdef")).isNull()
        assertThat(NotionPageReference.parse("https://evilnotion.com/page-0123456789abcdef0123456789abcdef")).isNull()
    }

    @Test
    fun `rejects ids that are not exactly 32 hexadecimal characters`() {
        assertThat(NotionPageReference.parse("not-a-page-id")).isNull()
    }

    @Test
    fun `uses the last page id in the Notion URL path instead of an earlier slug or query id`() {
        val actualPageId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        assertThat(
            NotionPageReference.parse(
                "https://workspace.notion.site/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-title-$actualPageId" +
                    "?other=cccccccccccccccccccccccccccccccc"
            )
        ).isEqualTo(NotionPageId(actualPageId))
    }
}
