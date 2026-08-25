package xyz.robinjoon.notionblog.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import xyz.robinjoon.notionblog.adapter.input.web.view.ExternalLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.InternalLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.ListView
import xyz.robinjoon.notionblog.adapter.input.web.view.MediaView
import xyz.robinjoon.notionblog.adapter.input.web.view.ParagraphView
import xyz.robinjoon.notionblog.adapter.input.web.view.TextInlineView
import xyz.robinjoon.notionblog.adapter.input.web.view.UnsupportedView
import xyz.robinjoon.notionblog.application.model.BlogPage
import xyz.robinjoon.notionblog.application.model.LinkResolution
import xyz.robinjoon.notionblog.application.model.PresentationAssetDescriptor
import xyz.robinjoon.notionblog.domain.post.Post
import xyz.robinjoon.notionblog.domain.post.PostId
import xyz.robinjoon.notionblog.domain.post.block.BlockId
import xyz.robinjoon.notionblog.domain.post.block.BlockNode
import xyz.robinjoon.notionblog.domain.post.block.BlockTree
import xyz.robinjoon.notionblog.domain.post.block.content.BlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.BlockIcon
import xyz.robinjoon.notionblog.domain.post.block.content.HeadingLevel
import xyz.robinjoon.notionblog.domain.post.block.content.LayoutBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.ListBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.MediaType
import xyz.robinjoon.notionblog.domain.post.block.content.MeetingNotesStatus
import xyz.robinjoon.notionblog.domain.post.block.content.NumberedListFormat
import xyz.robinjoon.notionblog.domain.post.block.content.ReferenceBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.ReusableBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.SpecialBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.SynchronizedBlockOrigin
import xyz.robinjoon.notionblog.domain.post.block.content.TextBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.UnsupportedBlockContent
import xyz.robinjoon.notionblog.domain.post.block.inline.InlineContent
import xyz.robinjoon.notionblog.domain.post.block.inline.LinkTarget
import xyz.robinjoon.notionblog.domain.post.block.inline.MentionKind
import xyz.robinjoon.notionblog.domain.post.block.inline.TextAnnotations
import xyz.robinjoon.notionblog.domain.post.block.media.MediaSource
import xyz.robinjoon.notionblog.domain.post.block.style.BlockStyle
import xyz.robinjoon.notionblog.domain.post.block.style.ColorToken
import xyz.robinjoon.notionblog.domain.post.block.style.StyleVariant
import xyz.robinjoon.notionblog.domain.post.block.style.WidthToken
import xyz.robinjoon.notionblog.domain.publication.PublicationId
import xyz.robinjoon.notionblog.domain.site.PresentationAssetRef
import xyz.robinjoon.notionblog.domain.site.PresentationProfile
import xyz.robinjoon.notionblog.domain.site.PresentationProfileId
import xyz.robinjoon.notionblog.domain.site.PresentationProfileKey
import xyz.robinjoon.notionblog.domain.site.PresentationProfileRef
import xyz.robinjoon.notionblog.domain.site.PresentationTokens
import xyz.robinjoon.notionblog.domain.site.SiteConfiguration
import xyz.robinjoon.notionblog.domain.site.SiteMetadata
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef
import xyz.robinjoon.notionblog.domain.source.SourceId
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PostPageViewAssemblerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC)
    private val assembler = PostPageViewAssembler(clock)

    @Test
    fun `renders only resolved internal or safe external links and expires source hosted media`() {
        val document = SourceDocumentRef(SourceId("notion"), "internal")
        val internal = LinkTarget.SourceDocument(document, URI("https://notion.so/internal"))
        val external = LinkTarget.ExternalUrl(URI("https://example.com/read"))
        val unsafe = LinkTarget.ExternalUrl(URI("javascript:alert(1)"))
        val page = page(
            nodes = listOf(
                node(
                    "paragraph",
                    TextBlockContent.Paragraph(
                        listOf(
                            InlineContent.Text(
                                "<script>alert(1)</script>",
                                TextAnnotations(bold = true, foreground = ColorToken.RED, background = ColorToken.BLUE),
                                internal,
                            ),
                            InlineContent.Text("external", link = external),
                            InlineContent.Text("unsafe", link = unsafe),
                            InlineContent.Equation("x &lt; y"),
                            InlineContent.Mention("Person", MentionKind.USER, target = external),
                        ),
                    ),
                ),
                node(
                    "expired-image",
                    MediaBlockContent.Media(
                        MediaType.IMAGE,
                        MediaSource.SourceHosted(URI("https://files.notion.so/image"), clock.instant().minusSeconds(1)),
                        "image.png",
                    ),
                ),
                node(
                    "unsafe-image",
                    MediaBlockContent.Media(MediaType.IMAGE, MediaSource.External(URI("data:image/png;base64,x")), "image.png"),
                ),
            ),
            links = mapOf(internal to LinkResolution.Internal(PostId(UUID.fromString("00000000-0000-0000-0000-000000000123")))),
        )

        val view = assembler.assemble(page)
        val paragraph = view.post.blocks.first() as ParagraphView
        val text = paragraph.content.filterIsInstance<TextInlineView>()

        assertThat((text[0].link as InternalLinkView).href).isEqualTo("/posts/00000000-0000-0000-0000-000000000123")
        assertThat((text[1].link as ExternalLinkView).href).isEqualTo("https://example.com/read")
        assertThat(text[2].link).isNull()
        assertThat(text[0].annotations.classes).contains("notion-color-red", "notion-background-blue")
        assertThat((view.post.blocks[1] as MediaView).url).isNull()
        assertThat((view.post.blocks[2] as MediaView).url).isNull()
    }

    @Test
    fun `maps style tokens through fixed view classes instead of user supplied values`() {
        val page = page(
            nodes = listOf(
                BlockNode(
                    BlockId("styled"),
                    TextBlockContent.Paragraph(listOf(InlineContent.Text("safe"))),
                    BlockStyle(foreground = ColorToken.RED, variant = StyleVariant("untrusted variant")),
                ),
            ),
        )

        val view = assembler.assemble(page)
        val paragraph = view.post.blocks.single() as ParagraphView

        assertThat(paragraph.style.classes).contains("notion-color-red")
        assertThat(paragraph.style.classes).doesNotContain("untrusted variant")
    }

    @Test
    fun `renders semantic list toggle table tabs and escaped content through thymeleaf`() {
        val page = page(
            nodes = listOf(
                BlockNode(
                    BlockId("parent-paragraph"),
                    TextBlockContent.Paragraph(listOf(InlineContent.Text("parent"))),
                    children = listOf(node("paragraph-child", TextBlockContent.Paragraph(listOf(InlineContent.Text("child"))))),
                ),
                node("bullet-1", ListBlockContent.BulletedItem(listOf(InlineContent.Text("one")))),
                node("bullet-2", ListBlockContent.BulletedItem(listOf(InlineContent.Text("two")))),
                node("todo", ListBlockContent.ToDoItem(listOf(InlineContent.Text("task")), true)),
                BlockNode(
                    BlockId("toggle"),
                    TextBlockContent.Toggle(listOf(InlineContent.Text("toggle"))),
                    children = listOf(node("toggle-child", TextBlockContent.Paragraph(listOf(InlineContent.Text("nested"))))),
                ),
                BlockNode(
                    BlockId("table"),
                    LayoutBlockContent.Table(width = 2, hasColumnHeader = true, hasRowHeader = true),
                    children = listOf(
                        BlockNode(BlockId("header-row"), LayoutBlockContent.TableRow(listOf(listOf(InlineContent.Text("name")), listOf(InlineContent.Text("value"))))),
                        BlockNode(BlockId("body-row"), LayoutBlockContent.TableRow(listOf(listOf(InlineContent.Text("row")), listOf(InlineContent.Text("cell"))))),
                    ),
                ),
                BlockNode(
                    BlockId("columns"),
                    LayoutBlockContent.ColumnList,
                    children = listOf(
                        BlockNode(BlockId("column"), LayoutBlockContent.Column(WidthToken(0.53)), children = listOf(node("column-text", TextBlockContent.Paragraph(listOf(InlineContent.Text("column")))))),
                    ),
                ),
                BlockNode(
                    BlockId("tabs"),
                    LayoutBlockContent.TabContainer,
                    children = listOf(
                        BlockNode(BlockId("tab"), LayoutBlockContent.TabItem(listOf(InlineContent.Text("Tab")), null), children = listOf(node("tab-text", TextBlockContent.Paragraph(listOf(InlineContent.Text("panel")))))),
                    ),
                ),
            ),
        )

        val html = templateEngine().process("blog/post", Context().apply { setVariable("page", assembler.assemble(page)) })

        assertThat(html).contains("<ul", "one", "two", "type=\"checkbox\"", " disabled")
        assertThat(html).contains("<details", "toggle", "<thead", "scope=\"col\"", "scope=\"row\"")
        assertThat(html).contains("notion-columns", "notion-column-width-55", "role=\"tablist\"", "role=\"tabpanel\"")
        assertThat(html).doesNotMatch(Regex("(?s)<p[^>]*>(?:(?!</p>).)*<section class=\"notion-children\"").toPattern())
        assertThat(html).doesNotContain("style=")
        assertThat(html).doesNotContain("th:utext")
    }

    @Test
    fun `maps every normalized block subtype without dropping nested fallback children`() {
        val reference = SourceDocumentRef(SourceId("notion"), "reference")
        val rich = listOf(InlineContent.Text("text"))
        val page = page(
            nodes = listOf(
                node("paragraph", TextBlockContent.Paragraph(rich)),
                node("heading-one", TextBlockContent.Heading(HeadingLevel.ONE, rich)),
                node("heading-two", TextBlockContent.Heading(HeadingLevel.TWO, rich)),
                node("heading-three", TextBlockContent.Heading(HeadingLevel.THREE, rich)),
                node("heading-four", TextBlockContent.Heading(HeadingLevel.FOUR, rich)),
                node("quote", TextBlockContent.Quote(rich)),
                node("toggle", TextBlockContent.Toggle(rich)),
                node("callout", TextBlockContent.Callout(rich, BlockIcon.Emoji("💡"))),
                node("code", TextBlockContent.Code(rich, "kotlin", rich)),
                node("equation", TextBlockContent.Equation("x = y")),
                node("bullet", ListBlockContent.BulletedItem(rich)),
                node("numbered", ListBlockContent.NumberedItem(rich, 3, NumberedListFormat.UPPER_ROMAN)),
                node("todo", ListBlockContent.ToDoItem(rich, true)),
                node("divider", LayoutBlockContent.Divider),
                BlockNode(BlockId("columns"), LayoutBlockContent.ColumnList, children = listOf(BlockNode(BlockId("column"), LayoutBlockContent.Column(WidthToken(0.5)), children = listOf(node("column-child", TextBlockContent.Paragraph(rich)))))),
                BlockNode(BlockId("tabs"), LayoutBlockContent.TabContainer, children = listOf(BlockNode(BlockId("tab"), LayoutBlockContent.TabItem(rich, null), children = listOf(node("tab-child", TextBlockContent.Paragraph(rich)))))),
                BlockNode(BlockId("table"), LayoutBlockContent.Table(1, true, true), children = listOf(BlockNode(BlockId("row"), LayoutBlockContent.TableRow(listOf(rich))))),
                node("image", MediaBlockContent.Media(MediaType.IMAGE, MediaSource.External(URI("https://example.com/image")), "image")),
                node("video", MediaBlockContent.Media(MediaType.VIDEO, MediaSource.External(URI("https://example.com/video")), "video")),
                node("audio", MediaBlockContent.Media(MediaType.AUDIO, MediaSource.External(URI("https://example.com/audio")), "audio")),
                node("file", MediaBlockContent.Media(MediaType.FILE, MediaSource.External(URI("https://example.com/file")), "file")),
                node("pdf", MediaBlockContent.Media(MediaType.PDF, MediaSource.External(URI("https://example.com/pdf")), "pdf")),
                node("bookmark", MediaBlockContent.Bookmark(URI("https://example.com/bookmark"), rich)),
                node("preview", MediaBlockContent.LinkPreview(URI("https://example.com/preview"))),
                node("embed", MediaBlockContent.Embed(URI("https://www.youtube.com/embed/id"), rich)),
                node("child", ReferenceBlockContent.ChildPost("Child", reference)),
                node("document", ReferenceBlockContent.DocumentLink(reference, URI("https://example.com/document"))),
                node("database", ReferenceBlockContent.DatabaseLink(reference, URI("https://example.com/database"))),
                node("breadcrumb", ReferenceBlockContent.Breadcrumb(listOf(LinkTarget.SourceDocument(reference, URI("https://example.com/breadcrumb"))))),
                node("toc", ReferenceBlockContent.TableOfContents),
                node("synced", ReusableBlockContent.Synchronized(SynchronizedBlockOrigin(reference, "origin"))),
                node("template", ReusableBlockContent.Template),
                node("meeting", SpecialBlockContent.MeetingNotes("Meeting", MeetingNotesStatus.COMPLETED, rich, null)),
                BlockNode(BlockId("unsupported"), UnsupportedBlockContent("future_block"), children = listOf(node("fallback-child", TextBlockContent.Paragraph(rich)))),
            ),
        )

        val blocks = assembler.assemble(page).post.blocks
        val kinds = blocks.map { it.kind.name }
        val listItems = blocks.filterIsInstance<ListView>().flatMap { it.items }.map { it::class.simpleName }

        assertThat(kinds).contains(
            "PARAGRAPH", "HEADING", "QUOTE", "TOGGLE", "CALLOUT", "CODE", "EQUATION", "LIST", "DIVIDER",
            "COLUMN_LIST", "TAB_CONTAINER", "TABLE", "MEDIA", "BOOKMARK", "LINK_PREVIEW", "EMBED", "CHILD_POST",
            "DOCUMENT_LINK", "DATABASE_LINK", "BREADCRUMB", "TABLE_OF_CONTENTS", "SYNCHRONIZED", "TEMPLATE",
            "MEETING_NOTES", "UNSUPPORTED",
        )
        assertThat(listItems).contains("BulletedListItemView", "NumberedListItemView", "TodoListItemView")
        assertThat((blocks.last() as UnsupportedView).originalType).isEqualTo("future_block")
        assertThat((blocks.last() as UnsupportedView).children).hasSize(1)
    }

    private fun page(
        nodes: List<BlockNode>,
        links: Map<LinkTarget.SourceDocument, LinkResolution> = emptyMap(),
    ): BlogPage {
        val style = PresentationAssetRef("style", 1, "sha256-style")
        val profile = PresentationProfile(
            PresentationProfileId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            PresentationProfileKey("default"),
            1,
            PresentationTokens(),
            listOf(style),
            emptyList(),
        )
        val root = SourceDocumentRef(SourceId("notion"), "root")
        return BlogPage(
            SiteConfiguration(
                PublicationId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                root,
                null,
                null,
                SiteMetadata("Blog", "Description", "en", null),
                PresentationProfileRef(profile.id, profile.version),
            ),
            profile,
            mapOf(style to PresentationAssetDescriptor("/presentation/notion.css", "text/css", style.integrity)),
            Post(PostId(UUID.fromString("00000000-0000-0000-0000-000000000003")), "Post", BlockTree(nodes)),
            null,
            null,
            links,
        )
    }

    private fun node(id: String, content: BlockContent): BlockNode = BlockNode(BlockId(id), content)

    private fun templateEngine(): SpringTemplateEngine = SpringTemplateEngine().apply {
        setTemplateResolver(
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
                isCacheable = false
            },
        )
    }
}
