package xyz.robinjoon.notionblog.adapter.out.rendering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
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
import xyz.robinjoon.notionblog.domain.model.RichTextAnnotations
import xyz.robinjoon.notionblog.domain.model.TableBlock
import xyz.robinjoon.notionblog.domain.model.TableRowBlock
import xyz.robinjoon.notionblog.domain.model.ToDoBlock
import xyz.robinjoon.notionblog.domain.model.ToggleBlock
import xyz.robinjoon.notionblog.domain.model.UnsupportedBlock
import xyz.robinjoon.notionblog.domain.model.VideoBlock

class NotionPageRendererTest {
    private lateinit var renderer: NotionPageRenderer

    @BeforeEach
    fun setUp() {
        val resolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = "UTF-8"
            isCacheable = false
        }
        renderer = NotionPageRenderer(SpringTemplateEngine().apply { setTemplateResolver(resolver) })
    }

    @Test
    fun `renders page title paragraphs headings and nested children`() {
        val html = renderer.render(
            title = "Hello",
            blocks = listOf(
                HeadingBlock("h1", HeadingLevel.ONE, rich("Title")),
                ParagraphBlock(
                    "p",
                    rich("Body"),
                    children = listOf(QuoteBlock("q", rich("Nested quote"))),
                ),
            ),
        )

        assertThat(html).contains("<h1", "Title", "<p", "Body", "Nested quote")
        assertThat(html).contains("notion-page")
    }

    @Test
    fun `groups only consecutive list items and keeps list kinds separate`() {
        val html = renderer.render(
            title = "Lists",
            blocks = listOf(
                BulletedListItemBlock("b1", rich("one")),
                BulletedListItemBlock("b2", rich("two")),
                ParagraphBlock("p", rich("between")),
                NumberedListItemBlock("n1", rich("first")),
                NumberedListItemBlock("n2", rich("second")),
                ToDoBlock("t1", rich("done"), checked = true),
            ),
        )

        assertThat(html).contains("<ul", "one", "two", "<ol", "first", "second")
        assertThat(html).contains("between", "done", "checked")
        assertThat(html.indexOf("one")).isLessThan(html.indexOf("between"))
        assertThat(html.indexOf("between")).isLessThan(html.indexOf("first"))
    }

    @Test
    fun `renders supported blocks media tables columns child pages and fallback`() {
        val pageId = NotionPageId("0123456789abcdef0123456789abcdef")
        val html = renderer.render(
            title = "All blocks",
            blocks = listOf(
                HeadingBlock("h2", HeadingLevel.TWO, rich("Heading")),
                HeadingBlock("h3", HeadingLevel.THREE, rich("Subheading")),
                ToDoBlock("todo", rich("Task"), checked = false),
                ToggleBlock("toggle", rich("Toggle"), children = listOf(ParagraphBlock("nested", rich("Hidden")))),
                QuoteBlock("quote", rich("Quote")),
                CalloutBlock("callout", rich("Callout"), icon = "💡"),
                DividerBlock("divider"),
                CodeBlock("code", rich("val x = 1"), language = "kotlin", caption = rich("Example")),
                ImageBlock("image", "https://cdn.example/image.png", caption = rich("Image caption")),
                VideoBlock("video", "https://cdn.example/video.mp4"),
                FileBlock("file", "https://cdn.example/file.pdf", "file.pdf"),
                BookmarkBlock("bookmark", "https://example.com/bookmark"),
                TableBlock("table", width = 2, children = listOf(TableRowBlock("row", listOf(rich("A"), rich("B"))))),
                ColumnBlock("column", children = listOf(ParagraphBlock("column-p", rich("Column text")))),
                ChildPageBlock("child", "Child page", pageId),
                UnsupportedBlock("unknown", "mystery_block"),
            ),
        )

        assertThat(html).contains(
            "notion-heading-2", "notion-heading-3", "notion-to-do", "notion-toggle",
            "notion-quote", "notion-callout", "💡", "notion-divider", "notion-code",
            "notion-image-element", "notion-video", "notion-file", "notion-bookmark",
            "notion-table", "<td", "notion-column", "Child page",
            "Unsupported Notion block: mystery_block",
        )
        assertThat(html).contains("language-kotlin")
    }

    @Test
    fun `renders rich text annotations and normalizes safe links`() {
        val html = renderer.render(
            title = "Links",
            blocks = listOf(
                ParagraphBlock(
                    "p",
                    listOf(
                        RichText(
                            plainText = "formatted",
                            annotations = RichTextAnnotations(
                                bold = true,
                                italic = true,
                                underline = true,
                                strikethrough = true,
                                code = true,
                            ),
                            link = "https://www.notion.so/My-Page-0123456789abcdef0123456789abcdef",
                        ),
                        RichText("external", link = "https://example.com/post"),
                    ),
                ),
            ),
        )

        assertThat(html).contains(
            "<strong>",
            "<em>",
            "<u>",
            "<s>",
            "<code>",
            "href=\"/notion/0123456789abcdef0123456789abcdef\"",
            "href=\"https://example.com/post\"",
        )
    }

    @Test
    fun `does not turn dangerous links into anchors and escapes text`() {
        val html = renderer.render(
            title = "Safety",
            blocks = listOf(
                ParagraphBlock(
                    "p",
                    listOf(
                        RichText("<script>alert(1)</script>", link = "javascript:alert(1)"),
                        RichText("bad bookmark", link = "data:text/html,pwned"),
                    ),
                ),
                BookmarkBlock("bookmark", "javascript:alert(1)"),
                ImageBlock("image", "javascript:alert(1)"),
            ),
        )

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
        assertThat(html).doesNotContain("javascript:")
        assertThat(html).doesNotContain("data:")
        assertThat(html).doesNotContain("<script>alert")
        assertThat(html).doesNotContain("<a href=\"javascript")
        assertThat(html).doesNotContain("<img")
        assertThat(html).contains("<head>", "<title>Safety</title>")
        assertThat(html).doesNotContain("<head><script")
    }

    private fun rich(text: String): List<RichText> = listOf(RichText(text))
}
