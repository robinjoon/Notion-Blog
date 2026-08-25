package xyz.robinjoon.notionblog.adapter.output.notion.mapping

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionBlockEnvelope
import xyz.robinjoon.notionblog.application.port.output.source.SourceMappingException
import xyz.robinjoon.notionblog.domain.post.block.BlockId
import xyz.robinjoon.notionblog.domain.post.block.BlockNode
import xyz.robinjoon.notionblog.domain.post.block.content.LayoutBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaType
import xyz.robinjoon.notionblog.domain.post.block.content.TextBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.UnsupportedBlockContent
import xyz.robinjoon.notionblog.domain.post.block.inline.InlineContent
import xyz.robinjoon.notionblog.domain.post.block.inline.LinkTarget
import xyz.robinjoon.notionblog.domain.post.block.media.MediaSource
import xyz.robinjoon.notionblog.domain.post.block.style.ColorToken
import xyz.robinjoon.notionblog.domain.source.SourceId
import java.time.Instant

class NotionBlockMapperTest {
    private val objectMapper = JsonMapper.builder().build()
    private val mapper = NotionBlockMapper(SourceId("notion-main"))

    @Test
    fun `recognizes every official 2026-03-11 block response alternative`() {
        val fixture = objectMapper.readTree(readResource("notion/2026-03-11/block-alternatives.json"))
        val alternatives = fixture.toList()
        val mapped = alternatives.map { entry ->
            mapper.map(
                NotionBlockEnvelope(
                    id = entry.required("id").stringValue(),
                    type = entry.required("type").stringValue(),
                    hasChildren = entry.required("has_children").asBoolean(),
                    inTrash = false,
                    payload = entry.required("payload"),
                ),
            )
        }

        assertThat(mapped).hasSize(36)
        assertThat(mapped.map { it.content::class.simpleName }).containsExactlyElementsOf(
            alternatives.map { it.required("expected").stringValue() },
        )
    }

    @Test
    fun `maps rich text variants mentions and annotation foreground background separately`() {
        val block = envelope(
            type = "paragraph",
            payload = """
                {
                  "rich_text": [
                    {"type":"text","text":{"content":"linked","link":{"url":"https://example.com"}},"annotations":{"bold":true,"color":"red_background"},"plain_text":"linked","href":"https://example.com"},
                    {"type":"equation","equation":{"expression":"E=mc^2"},"annotations":{"italic":true,"color":"blue"},"plain_text":"E=mc^2","href":null},
                    {"type":"mention","mention":{"type":"page","page":{"id":"a1b2c3d4e5f64a5b8c9d0e1f2a3b4c5d"}},"annotations":{"color":"default"},"plain_text":"Page","href":"https://www.notion.so/page-1"},
                    {"type":"mention","mention":{"type":"database","database":{"id":"database-1"}},"annotations":{"color":"default"},"plain_text":"Database","href":null},
                    {"type":"mention","mention":{"type":"date","date":{"start":"2026-01-01"}},"annotations":{"color":"default"},"plain_text":"Jan 1","href":null},
                    {"type":"mention","mention":{"type":"template_mention","template_mention":{"type":"template_mention_date","template_mention_date":"today"}},"annotations":{"color":"default"},"plain_text":"Today","href":null},
                    {"type":"mention","mention":{"type":"link_preview","link_preview":{"url":"https://example.com/preview"}},"annotations":{"color":"default"},"plain_text":"Preview","href":"https://example.com/preview"},
                    {"type":"mention","mention":{"type":"user","user":{"id":"user-1"}},"annotations":{"color":"default"},"plain_text":"Ada","href":null}
                  ],
                  "color":"green_background"
                }
            """.trimIndent(),
        )

        val mapped = mapper.map(block)
        val content = mapped.content as TextBlockContent.Paragraph

        assertThat(mapped.style.foreground).isNull()
        assertThat(mapped.style.background).isEqualTo(ColorToken.GREEN)
        val linked = content.richText[0] as InlineContent.Text
        assertThat(linked.text).isEqualTo("linked")
        assertThat(linked.annotations.bold).isTrue()
        assertThat(linked.annotations.foreground).isNull()
        assertThat(linked.annotations.background).isEqualTo(ColorToken.RED)
        assertThat(content.richText).hasSize(8)
        assertThat(content.richText[1]).isInstanceOf(InlineContent.Equation::class.java)
        assertThat(content.richText.drop(2).map { (it as InlineContent.Mention).kind.name }).containsExactly(
            "DOCUMENT",
            "DATABASE",
            "DATE",
            "TEMPLATE",
            "LINK_PREVIEW",
            "USER",
        )
    }

    @Test
    fun `maps source hosted media expiry and external media type`() {
        val hosted = mapper.map(
            envelope(
                type = "image",
                payload = """{"type":"file","file":{"url":"https://secure.notion-static.com/image.png","expiry_time":"2026-08-25T12:30:00Z"},"caption":[]}""",
            ),
        ).content as MediaBlockContent.Media
        val external = mapper.map(
            envelope(
                type = "audio",
                payload = """{"type":"external","external":{"url":"https://example.com/audio.mp3"},"caption":[]}""",
            ),
        ).content as MediaBlockContent.Media

        assertThat(hosted.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(hosted.source).isEqualTo(
            MediaSource.SourceHosted(
                java.net.URI("https://secure.notion-static.com/image.png"),
                Instant.parse("2026-08-25T12:30:00Z"),
            ),
        )
        assertThat(external.mediaType).isEqualTo(MediaType.AUDIO)
        assertThat(external.source).isEqualTo(MediaSource.External(java.net.URI("https://example.com/audio.mp3")))
    }

    @Test
    fun `normalizes tab paragraph children to tab items and preserves their nested content`() {
        val tabChild = BlockNode(
            id = BlockId("tab-title"),
            content = TextBlockContent.Paragraph(listOf(InlineContent.Text("Overview"))),
            children = listOf(
                BlockNode(
                    id = BlockId("tab-body"),
                    content = TextBlockContent.Paragraph(listOf(InlineContent.Text("Body"))),
                ),
            ),
        )

        val mapped = mapper.map(envelope(type = "tab", payload = "{}"), listOf(tabChild))

        assertThat(mapped.content).isEqualTo(LayoutBlockContent.TabContainer)
        assertThat(mapped.children.single().content).isEqualTo(
            LayoutBlockContent.TabItem(listOf(InlineContent.Text("Overview")), null),
        )
        assertThat(mapped.children.single().children.single().id).isEqualTo(BlockId("tab-body"))
    }

    @Test
    fun `maps a tab paragraph's title and icon when its caller supplies tab context`() {
        val tabItem = mapper.mapTabItem(
            envelope(
                type = "paragraph",
                payload = """{"rich_text":[{"type":"text","text":{"content":"Overview","link":null},"annotations":{"color":"default"},"plain_text":"Overview","href":null}],"icon":{"emoji":"📋"}}""",
            ),
        )

        assertThat(tabItem.content).isEqualTo(
            LayoutBlockContent.TabItem(
                title = listOf(InlineContent.Text("Overview")),
                icon = xyz.robinjoon.notionblog.domain.post.block.content.BlockIcon.Emoji("📋"),
            ),
        )
    }

    @Test
    fun `rejects malformed known blocks but keeps an unknown type as a child preserving fallback`() {
        assertThatThrownBy { mapper.map(envelope(type = "code", payload = "{\"rich_text\":[]}")) }
            .isInstanceOf(NotionBlockMappingException::class.java)

        val child = BlockNode(BlockId("child"), TextBlockContent.Paragraph(emptyList()))
        val fallback = mapper.map(envelope(type = "future_widget", payload = "{}"), listOf(child))

        assertThat(fallback.content).isEqualTo(UnsupportedBlockContent("future_widget"))
        assertThat(fallback.children).containsExactly(child)
    }

    @Test
    fun `requires unsupported payload to retain its open underlying block type`() {
        assertThatThrownBy { mapper.map(envelope(type = "unsupported", payload = "{}")) }
            .isInstanceOf(NotionBlockMappingException::class.java)
    }

    @Test
    fun `canonicalizes every page source reference to lowercase thirty two hexadecimal characters`() {
        val childPage = mapper.map(
            envelope(
                type = "child_page",
                id = "A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D",
                payload = """{"title":"Child"}""",
            ),
        ).content as xyz.robinjoon.notionblog.domain.post.block.content.ReferenceBlockContent.ChildPost
        val pageLink = mapper.map(
            envelope(
                type = "link_to_page",
                payload = """{"type":"page_id","page_id":"A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D"}""",
            ),
        ).content as xyz.robinjoon.notionblog.domain.post.block.content.ReferenceBlockContent.DocumentLink
        val mention = mapper.map(
            envelope(
                type = "paragraph",
                payload = """
                    {"rich_text":[
                      {"type":"mention","mention":{"type":"page","page":{"id":"A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D"}},"annotations":{"color":"default"},"plain_text":"Page","href":null}
                    ]}
                """.trimIndent(),
            ),
        ).content as TextBlockContent.Paragraph

        assertThat(childPage.reference.externalId).isEqualTo("a1b2c3d4e5f64a5b8c9d0e1f2a3b4c5d")
        assertThat(pageLink.reference.externalId).isEqualTo("a1b2c3d4e5f64a5b8c9d0e1f2a3b4c5d")
        assertThat(((mention.richText.single() as InlineContent.Mention).target as LinkTarget.SourceDocument).reference.externalId)
            .isEqualTo("a1b2c3d4e5f64a5b8c9d0e1f2a3b4c5d")
    }

    @Test
    fun `rejects malformed page ids at every page reference mapping boundary`() {
        val malformedChildPage = envelope(
            type = "child_page",
            id = "not-a-page-id",
            payload = """{"title":"Child"}""",
        )
        val malformedPageLink = envelope(
            type = "link_to_page",
            payload = """{"type":"page_id","page_id":"not-a-page-id"}""",
        )
        val malformedMention = envelope(
            type = "paragraph",
            payload = """
                {"rich_text":[
                  {"type":"mention","mention":{"type":"page","page":{"id":"not-a-page-id"}},"annotations":{"color":"default"},"plain_text":"Page","href":null}
                ]}
            """.trimIndent(),
        )

        listOf(malformedChildPage, malformedPageLink, malformedMention).forEach { block ->
            assertThatThrownBy { mapper.map(block) }.isInstanceOf(SourceMappingException::class.java)
        }
    }

    private fun envelope(
        type: String,
        payload: String,
        id: String = "block-$type",
    ): NotionBlockEnvelope = NotionBlockEnvelope(
        id = id,
        type = type,
        hasChildren = false,
        inTrash = false,
        payload = objectMapper.readTree(payload),
    )

    private fun readResource(path: String): String = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "missing resource $path" }
        .bufferedReader()
        .use { it.readText() }

    private fun JsonNode.required(name: String): JsonNode = get(name) ?: error("missing $name")
}
