package xyz.robinjoon.notionblog.adapter.out.notion

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.out.notion.NotionAuthenticationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionConfigurationException
import xyz.robinjoon.notionblog.application.port.out.notion.NotionSettingKind
import xyz.robinjoon.notionblog.application.port.out.notion.RetryableNotionException
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
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.NumberedListItemBlock
import xyz.robinjoon.notionblog.domain.model.ParagraphBlock
import xyz.robinjoon.notionblog.domain.model.QuoteBlock
import xyz.robinjoon.notionblog.domain.model.RichTextColor
import xyz.robinjoon.notionblog.domain.model.TableBlock
import xyz.robinjoon.notionblog.domain.model.TableRowBlock
import xyz.robinjoon.notionblog.domain.model.ToDoBlock
import xyz.robinjoon.notionblog.domain.model.ToggleBlock
import xyz.robinjoon.notionblog.domain.model.UnsupportedBlock
import xyz.robinjoon.notionblog.domain.model.VideoBlock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class NotionRestClientAdapterTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `retrieves public page metadata and concatenates every title segment`() {
        server.enqueueJson(
            """
            {
              "id": "01234567-89AB-CDEF-0123-456789ABCDEF",
              "url": "https://www.notion.so/Test-0123456789abcdef0123456789abcdef",
              "public_url": "https://site.notion.site/Test-0123456789abcdef0123456789abcdef",
              "last_edited_time": "2026-06-29T00:00:00Z",
              "properties": {
                "Ignored": {"type": "rich_text", "rich_text": [{"plain_text": "No"}]},
                "Name": {
                  "type": "title",
                  "title": [{"plain_text": "Hello "}, {"plain_text": "Notion"}]
                }
              }
            }
            """,
        )

        val metadata = adapter().retrievePage(PAGE_ID)

        assertThat(metadata.id).isEqualTo(PAGE_ID)
        assertThat(metadata.title).isEqualTo("Hello Notion")
        assertThat(metadata.notionUrl).isEqualTo("https://www.notion.so/Test-0123456789abcdef0123456789abcdef")
        assertThat(metadata.publicUrl).isEqualTo("https://site.notion.site/Test-0123456789abcdef0123456789abcdef")
        assertThat(metadata.lastEditedAt).isEqualTo(Instant.parse("2026-06-29T00:00:00Z"))

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/v1/pages/${PAGE_ID.value}")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-secret")
        assertThat(request.getHeader("Notion-Version")).isEqualTo("2026-03-11")
    }

    @Test
    fun `accepts only the official production origin or a loopback test server`() {
        assertThatCode {
            NotionRestClientAdapter(
                baseUrl = "https://api.notion.com/v1",
                token = "test-secret",
                apiVersion = "2026-03-11",
            )
        }.doesNotThrowAnyException()

        listOf(
            "http://api.notion.com/v1",
            "https://api.notion.com:8443/v1",
            "https://user@api.notion.com/v1",
            "https://api.notion.com.evil.example/v1",
            "https://example.com/v1",
        ).forEach { baseUrl ->
            assertThatIllegalArgumentException()
                .isThrownBy {
                    NotionRestClientAdapter(
                        baseUrl = baseUrl,
                        token = "test-secret",
                        apiVersion = "2026-03-11",
                    )
                }
                .withMessageContaining("Notion base URL")
        }

        assertThatCode { adapter() }.doesNotThrowAnyException()
    }

    @Test
    fun `queries every data source cursor page and maps settings rows`() {
        server.enqueueJson(
            """
            {
              "results": [{
                "properties": {
                  "Key": {"type": "title", "title": [{"plain_text": "rootPage"}]},
                  "Kind": {"type": "select", "select": {"name": "page"}},
                  "Enabled": {"type": "checkbox", "checkbox": true},
                  "Page": {"type": "url", "url": "https://workspace.notion.site/Root-${PAGE_ID.value}"},
                  "Data": {"type": "rich_text", "rich_text": []}
                }
              }],
              "has_more": true,
              "next_cursor": "cursor-2"
            }
            """,
        )
        server.enqueueJson(
            """
            {
              "results": [{
                "properties": {
                  "Key": {"type": "title", "title": [{"plain_text": "head"}]},
                  "Kind": {"type": "select", "select": {"name": "head"}},
                  "Enabled": {"type": "checkbox", "checkbox": true},
                  "Page": {"type": "rich_text", "rich_text": []},
                  "Data": {"type": "rich_text", "rich_text": [{"plain_text": "{\"siteName\":\"Blog\"}"}]}
                }
              }],
              "has_more": false,
              "next_cursor": null
            }
            """,
        )

        val rows = adapter().querySettingsDataSource("settings-source")

        assertThat(rows).hasSize(2)
        assertThat(rows[0].key).isEqualTo("rootPage")
        assertThat(rows[0].kind).isEqualTo(NotionSettingKind.PAGE)
        assertThat(rows[0].enabled).isTrue()
        assertThat(rows[0].page).contains(PAGE_ID.value)
        assertThat(rows[0].data).isEmpty()
        assertThat(rows[1].kind).isEqualTo(NotionSettingKind.HEAD)
        assertThat(rows[1].data).isEqualTo("{\"siteName\":\"Blog\"}")

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertThat(first.path).isEqualTo("/v1/data_sources/settings-source/query")
        assertThat(first.body.readUtf8()).contains("\"page_size\":100").doesNotContain("start_cursor")
        assertThat(second.path).isEqualTo("/v1/data_sources/settings-source/query")
        assertThat(second.body.readUtf8()).contains("\"start_cursor\":\"cursor-2\"")
    }

    @Test
    fun `collects all block cursors recursively and discovers linked notion pages`() {
        server.enqueueJson(
            """
            {
              "results": [{
                "id": "paragraph-parent",
                "type": "paragraph",
                "has_children": true,
                "paragraph": {"rich_text": [{
                  "plain_text": "Parent",
                  "href": "https://www.notion.so/Linked-11111111111111111111111111111111"
                }]}
              }],
              "has_more": true,
              "next_cursor": "root-cursor-2"
            }
            """,
        )
        server.enqueueJson(
            """
            {
              "results": [
                {
                  "id": "bookmark-a",
                  "type": "bookmark",
                  "has_children": false,
                  "bookmark": {
                    "url": "https://workspace.notion.site/Another-22222222222222222222222222222222",
                    "caption": []
                  }
                },
                {
                  "id": "33333333-3333-3333-3333-333333333333",
                  "type": "child_page",
                  "has_children": false,
                  "child_page": {"title": "Child"}
                }
              ],
              "has_more": false,
              "next_cursor": null
            }
            """,
        )
        server.enqueueJson(
            """
            {
              "results": [{
                "id": "todo-child",
                "type": "to_do",
                "has_children": false,
                "to_do": {
                  "checked": true,
                  "rich_text": [{
                    "plain_text": "Mention",
                    "mention": {"type": "page", "page": {"id": "44444444-4444-4444-4444-444444444444"}}
                  }]
                }
              }],
              "has_more": false,
              "next_cursor": null
            }
            """,
        )

        val content = adapter().retrievePageContent(PAGE_ID)

        assertThat(content.blocks).hasSize(3)
        assertThat(content.blocks[0]).isEqualTo(
            ParagraphBlock(
                id = "paragraph-parent",
                richText = listOf(
                    xyz.robinjoon.notionblog.domain.model.RichText(
                        plainText = "Parent",
                        link = "https://www.notion.so/Linked-11111111111111111111111111111111",
                    ),
                ),
                children = listOf(
                    ToDoBlock(
                        id = "todo-child",
                        richText = listOf(xyz.robinjoon.notionblog.domain.model.RichText("Mention")),
                        checked = true,
                    ),
                ),
            ),
        )
        assertThat(content.blocks[1]).isInstanceOf(BookmarkBlock::class.java)
        assertThat(content.blocks[2]).isEqualTo(
            ChildPageBlock(
                id = "33333333-3333-3333-3333-333333333333",
                title = "Child",
                pageId = NotionPageId("33333333333333333333333333333333"),
            ),
        )
        assertThat(content.linkedPageIds).containsExactlyInAnyOrder(
            NotionPageId("11111111111111111111111111111111"),
            NotionPageId("22222222222222222222222222222222"),
            NotionPageId("33333333333333333333333333333333"),
            NotionPageId("44444444444444444444444444444444"),
        )

        val firstRoot = server.takeRequest()
        val secondRoot = server.takeRequest()
        val child = server.takeRequest()
        assertThat(firstRoot.path).isEqualTo("/v1/blocks/${PAGE_ID.value}/children?page_size=100")
        assertThat(secondRoot.path).contains("/v1/blocks/${PAGE_ID.value}/children").contains("start_cursor=root-cursor-2")
        assertThat(child.path).isEqualTo("/v1/blocks/paragraph-parent/children?page_size=100")
    }

    @Test
    fun `maps rich text annotations and the major supported block variants`() {
        server.enqueueJson(
            """
            {
              "results": [
                {"id":"heading","type":"heading_2","has_children":false,"heading_2":{"rich_text":[{"plain_text":"Heading","annotations":{"bold":true,"italic":true,"strikethrough":true,"underline":true,"code":true,"color":"red"}}],"is_toggleable":true}},
                {"id":"bullet","type":"bulleted_list_item","has_children":false,"bulleted_list_item":{"rich_text":[]}},
                {"id":"number","type":"numbered_list_item","has_children":false,"numbered_list_item":{"rich_text":[]}},
                {"id":"toggle","type":"toggle","has_children":false,"toggle":{"rich_text":[]}},
                {"id":"quote","type":"quote","has_children":false,"quote":{"rich_text":[]}},
                {"id":"callout","type":"callout","has_children":false,"callout":{"rich_text":[],"icon":{"type":"emoji","emoji":"💡"}}},
                {"id":"divider","type":"divider","has_children":false,"divider":{}},
                {"id":"code","type":"code","has_children":false,"code":{"rich_text":[{"plain_text":"println()"}],"language":"kotlin","caption":[{"plain_text":"sample"}]}},
                {"id":"image","type":"image","has_children":false,"image":{"type":"external","external":{"url":"https://example.com/image.png"},"caption":[]}},
                {"id":"video","type":"video","has_children":false,"video":{"type":"file","file":{"url":"https://example.com/video.mp4"},"caption":[]}},
                {"id":"file","type":"file","has_children":false,"file":{"type":"external","external":{"url":"https://example.com/file.pdf"},"name":"guide.pdf","caption":[]}},
                {"id":"table","type":"table","has_children":false,"table":{"table_width":2,"has_column_header":true,"has_row_header":false}},
                {"id":"row","type":"table_row","has_children":false,"table_row":{"cells":[[{"plain_text":"a"}],[{"plain_text":"b"}]]}},
                {"id":"column","type":"column","has_children":false,"column":{}},
                {"id":"mystery","type":"synced_block","has_children":false,"synced_block":{}}
              ],
              "has_more": false,
              "next_cursor": null
            }
            """,
        )

        val blocks = adapter().retrievePageContent(PAGE_ID).blocks

        assertThat(blocks).hasSize(15)
        val heading = blocks[0] as HeadingBlock
        assertThat(heading.level).isEqualTo(HeadingLevel.TWO)
        assertThat(heading.isToggleable).isTrue()
        assertThat(heading.richText.single().annotations.bold).isTrue()
        assertThat(heading.richText.single().annotations.italic).isTrue()
        assertThat(heading.richText.single().annotations.strikethrough).isTrue()
        assertThat(heading.richText.single().annotations.underline).isTrue()
        assertThat(heading.richText.single().annotations.code).isTrue()
        assertThat(heading.richText.single().annotations.color).isEqualTo(RichTextColor.RED)
        assertThat(blocks[1]).isInstanceOf(BulletedListItemBlock::class.java)
        assertThat(blocks[2]).isInstanceOf(NumberedListItemBlock::class.java)
        assertThat(blocks[3]).isInstanceOf(ToggleBlock::class.java)
        assertThat(blocks[4]).isInstanceOf(QuoteBlock::class.java)
        assertThat((blocks[5] as CalloutBlock).icon).isEqualTo("💡")
        assertThat(blocks[6]).isInstanceOf(DividerBlock::class.java)
        assertThat((blocks[7] as CodeBlock).language).isEqualTo("kotlin")
        assertThat((blocks[8] as ImageBlock).url).isEqualTo("https://example.com/image.png")
        assertThat((blocks[9] as VideoBlock).url).isEqualTo("https://example.com/video.mp4")
        assertThat((blocks[10] as FileBlock).name).isEqualTo("guide.pdf")
        assertThat(blocks[11]).isEqualTo(TableBlock("table", 2, hasColumnHeader = true))
        assertThat((blocks[12] as TableRowBlock).cells).hasSize(2)
        assertThat(blocks[13]).isInstanceOf(ColumnBlock::class.java)
        assertThat(blocks[14]).isEqualTo(UnsupportedBlock("mystery", "synced_block"))
    }

    @Test
    fun `discovers a page id from a link to page block`() {
        server.enqueueJson(
            """
            {
              "results": [{
                "id": "link-block",
                "type": "link_to_page",
                "has_children": false,
                "link_to_page": {
                  "type": "page_id",
                  "page_id": "55555555-5555-5555-5555-555555555555"
                }
              }],
              "has_more": false,
              "next_cursor": null
            }
            """,
        )

        val content = adapter().retrievePageContent(PAGE_ID)

        assertThat(content.blocks).containsExactly(UnsupportedBlock("link-block", "link_to_page"))
        assertThat(content.linkedPageIds).containsExactly(NotionPageId("55555555555555555555555555555555"))
    }

    @Test
    fun `stops cursor collection at the overall deadline even when each request meets its timeout`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"results":[],"has_more":true,"next_cursor":"cursor-2"}
                    """.trimIndent(),
                )
                .setBodyDelay(600, TimeUnit.MILLISECONDS),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"results":[],"has_more":false,"next_cursor":null}
                    """.trimIndent(),
                )
                .setBodyDelay(600, TimeUnit.MILLISECONDS),
        )

        assertThatThrownBy {
            adapter(
                requestTimeout = Duration.ofSeconds(2),
                totalCollectionTimeout = Duration.ofMillis(900),
            ).retrievePageContent(PAGE_ID)
        }.isInstanceOf(RetryableNotionException::class.java)
            .hasMessageContaining("deadline")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `classifies rate limits server failures and timeouts as retryable`() {
        for (status in listOf(429, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("{\"message\":\"sensitive remote body\"}"))
            assertThatThrownBy { adapter().retrievePage(PAGE_ID) }
                .isInstanceOf(RetryableNotionException::class.java)
                .hasMessageNotContaining("sensitive remote body")
        }

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThatThrownBy {
            adapter(requestTimeout = Duration.ofMillis(100)).retrievePage(PAGE_ID)
        }.isInstanceOf(RetryableNotionException::class.java)
    }

    @Test
    fun `classifies authentication and invalid configuration failures as non retryable`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"token detail\"}"))
        assertThatThrownBy { adapter().retrievePage(PAGE_ID) }
            .isInstanceOf(NotionAuthenticationException::class.java)
            .hasMessageNotContaining("token detail")

        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"message\":\"resource detail\"}"))
        assertThatThrownBy { adapter().querySettingsDataSource("wrong-id") }
            .isInstanceOf(NotionConfigurationException::class.java)
            .hasMessageNotContaining("resource detail")
    }

    private fun adapter(
        requestTimeout: Duration = Duration.ofSeconds(2),
        totalCollectionTimeout: Duration = Duration.ofSeconds(10),
    ): NotionRestClientAdapter =
        NotionRestClientAdapter(
            baseUrl = server.url("/v1").toString(),
            token = "test-secret",
            apiVersion = "2026-03-11",
            requestTimeout = requestTimeout,
            totalCollectionTimeout = totalCollectionTimeout,
        )

    private fun MockWebServer.enqueueJson(body: String) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body.trimIndent()),
        )
    }

    private companion object {
        val PAGE_ID = NotionPageId("0123456789abcdef0123456789abcdef")
    }
}
