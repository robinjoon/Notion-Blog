package xyz.robinjoon.notionblog.adapter.output.notion

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.adapter.output.notion.client.NotionApiClient
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionBlockEnvelope
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionPageParentResponse
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionPageResponse
import xyz.robinjoon.notionblog.application.model.ImportedPublicationStatus
import xyz.robinjoon.notionblog.application.port.output.source.RetryableSourceException
import xyz.robinjoon.notionblog.application.port.output.source.SourceAccessException
import xyz.robinjoon.notionblog.application.port.output.source.SourceConfigurationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceMappingException
import xyz.robinjoon.notionblog.domain.post.block.content.LayoutBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.ReferenceBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.TextBlockContent
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef
import xyz.robinjoon.notionblog.domain.source.SourceId
import java.time.Duration

class NotionPostSourceTest {
    private val sourceId = SourceId("notion-main")
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `returns published page with nested blocks and confirmed child pages without inlining child content`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block("paragraph-1", "paragraph", hasChildren = true, payload = """{"rich_text":[]}"""),
            block("discarded", "paragraph", payload = """{"rich_text":[]}""", inTrash = true),
            block(CHILD_DASHED_UPPER, "child_page", hasChildren = true, payload = """{"title":"Child"}"""),
        )
        every { client.fetchDirectBlockChildren("paragraph-1") } returns listOf(
            block("paragraph-2", "paragraph", payload = """{"rich_text":[]}"""),
        )
        every { client.fetchPage(CHILD) } returns page(CHILD, parent = NotionPageParentResponse("page_id", ROOT_DASHED_UPPER))

        val imported = source(client).fetch(reference(ROOT_DASHED_UPPER))

        assertThat(imported.sourceDocument).isEqualTo(reference(ROOT))
        assertThat(imported.publicationStatus).isEqualTo(ImportedPublicationStatus.PUBLISHED)
        assertThat(imported.content.roots).hasSize(2)
        assertThat(imported.content.roots.first().content).isInstanceOf(TextBlockContent.Paragraph::class.java)
        assertThat(imported.content.roots.first().children.map { it.id.value }).containsExactly("paragraph-2")
        assertThat(imported.content.roots[1].content).isEqualTo(ReferenceBlockContent.ChildPost("Child", reference(CHILD)))
        assertThat(imported.content.roots[1].children).isEmpty()
        assertThat(imported.containedChildren).containsExactly(reference(CHILD))
        verify(exactly = 1) { client.fetchDirectBlockChildren(ROOT) }
        verify(exactly = 0) { client.fetchDirectBlockChildren(CHILD) }
    }

    @Test
    fun `keeps discovering structural children of an unpublished parent`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT, publicUrl = null)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block(CHILD, "child_page", payload = """{"title":"Child"}"""),
        )
        every { client.fetchPage(CHILD) } returns page(CHILD, parent = NotionPageParentResponse("page_id", ROOT))

        val imported = source(client).fetch(reference(ROOT))

        assertThat(imported.publicationStatus).isEqualTo(ImportedPublicationStatus.UNPUBLISHED)
        assertThat(imported.containedChildren).containsExactly(reference(CHILD))
    }

    @Test
    fun `does not treat page links or child pages with another parent as contained children`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block("linked", "link_to_page", payload = """{"type":"page_id","page_id":"$OUTSIDE"}"""),
            block(CHILD, "child_page", payload = """{"title":"Link only"}"""),
        )
        every { client.fetchPage(CHILD) } returns page(CHILD, parent = NotionPageParentResponse("page_id", OTHER_PARENT))

        val imported = source(client).fetch(reference(ROOT))

        assertThat(imported.containedChildren).isEmpty()
        assertThat(imported.content.roots).hasSize(2)
        verify(exactly = 0) { client.fetchPage(OUTSIDE) }
    }

    @Test
    fun `fails the whole fetch when child parent verification cannot be completed`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block(CHILD, "child_page", payload = """{"title":"Child"}"""),
        )
        every { client.fetchPage(CHILD) } throws SourceAccessException("not available")

        assertThatThrownBy { source(client).fetch(reference(ROOT)) }
            .isInstanceOf(SourceAccessException::class.java)
    }

    @Test
    fun `uses the tab item mapper for direct paragraph children`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(block("tab", "tab", hasChildren = true, payload = "{}"))
        every { client.fetchDirectBlockChildren("tab") } returns listOf(
            block(
                "tab-title",
                "paragraph",
                payload = """{"rich_text":[],"icon":{"emoji":"📋"}}""",
            ),
        )

        val imported = source(client).fetch(reference(ROOT))

        val tab = imported.content.roots.single()
        assertThat(tab.content).isEqualTo(LayoutBlockContent.TabContainer)
        assertThat(tab.children.single().content).isInstanceOf(LayoutBlockContent.TabItem::class.java)
    }

    @Test
    fun `rejects source mismatch before fetching and mapping limit violations`() {
        val client = mockk<NotionApiClient>()
        assertThatThrownBy { source(client).fetch(SourceDocumentRef(SourceId("other"), ROOT)) }
            .isInstanceOf(SourceConfigurationException::class.java)
        verify(exactly = 0) { client.fetchPage(any()) }

        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block("one", "paragraph", payload = """{"rich_text":[]}"""),
            block("two", "paragraph", payload = """{"rich_text":[]}"""),
        )
        assertThatThrownBy { source(client, maxBlockCount = 1).fetch(reference(ROOT)) }
            .isInstanceOf(SourceMappingException::class.java)
    }

    @Test
    fun `rejects nesting beyond the configured depth and total deadline`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block("one", "paragraph", hasChildren = true, payload = """{"rich_text":[]}"""),
        )
        every { client.fetchDirectBlockChildren("one") } returns listOf(
            block("two", "paragraph", payload = """{"rich_text":[]}"""),
        )
        assertThatThrownBy { source(client, maxDepth = 1).fetch(reference(ROOT)) }
            .isInstanceOf(SourceMappingException::class.java)

        var timestampIndex = 0
        val timestamps = longArrayOf(0, 2)
        assertThatThrownBy {
            source(client, collectionTimeout = Duration.ofNanos(1), nanoTime = { timestamps[timestampIndex++] })
                .fetch(reference(ROOT))
        }.isInstanceOf(RetryableSourceException::class.java)
    }

    @Test
    fun `rejects repeated block identifiers encountered through a recursive collection`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page(ROOT)
        every { client.fetchDirectBlockChildren(ROOT) } returns listOf(
            block("repeated", "paragraph", hasChildren = true, payload = """{"rich_text":[]}"""),
        )
        every { client.fetchDirectBlockChildren("repeated") } returns listOf(
            block("repeated", "paragraph", payload = """{"rich_text":[]}"""),
        )

        assertThatThrownBy { source(client).fetch(reference(ROOT)) }
            .isInstanceOf(SourceMappingException::class.java)
    }

    @Test
    fun `rejects malformed page ids returned by the Notion API`() {
        val client = mockk<NotionApiClient>()
        every { client.fetchPage(ROOT) } returns page("not-a-notion-id")

        assertThatThrownBy { source(client).fetch(reference(ROOT)) }
            .isInstanceOf(SourceMappingException::class.java)
    }

    private fun source(
        client: NotionApiClient,
        maxDepth: Int = 8,
        maxBlockCount: Int = 100,
        collectionTimeout: Duration = Duration.ofSeconds(1),
        nanoTime: () -> Long = System::nanoTime,
    ) = NotionPostSource(
        sourceId = sourceId,
        client = client,
        maxDepth = maxDepth,
        maxBlockCount = maxBlockCount,
        collectionTimeout = collectionTimeout,
        nanoTime = nanoTime,
    )

    private fun reference(externalId: String) = SourceDocumentRef(sourceId, externalId)

    private fun page(
        id: String,
        parent: NotionPageParentResponse = NotionPageParentResponse("workspace", null),
        publicUrl: String? = "https://workspace.notion.site/$id",
    ) = NotionPageResponse(
        id = id,
        parent = parent,
        url = "https://www.notion.so/$id",
        publicUrl = publicUrl,
        inTrash = false,
        lastEditedTime = "2026-08-25T00:00:00Z",
        properties = objectMapper.readTree("""{"Name":{"type":"title","title":[{"plain_text":"$id"}]}}"""),
    )

    private fun block(
        id: String,
        type: String,
        hasChildren: Boolean = false,
        payload: String,
        inTrash: Boolean = false,
    ) = NotionBlockEnvelope(id, type, hasChildren, inTrash, objectMapper.readTree(payload))

    private companion object {
        const val ROOT = "a0b1c2d3e4f56789abcdef0123456789"
        const val ROOT_DASHED_UPPER = "A0B1C2D3-E4F5-6789-ABCD-EF0123456789"
        const val CHILD = "0123456789abcdef0123456789abcdef"
        const val CHILD_DASHED_UPPER = "01234567-89AB-CDEF-0123-456789ABCDEF"
        const val OTHER_PARENT = "fedcba9876543210fedcba9876543210"
        const val OUTSIDE = "1234567890abcdef1234567890abcdef"
    }
}
