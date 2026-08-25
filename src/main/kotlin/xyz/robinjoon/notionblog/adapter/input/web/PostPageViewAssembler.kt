package xyz.robinjoon.notionblog.adapter.input.web

import xyz.robinjoon.notionblog.adapter.input.web.view.BlockEquationView
import xyz.robinjoon.notionblog.adapter.input.web.view.BlockIconView
import xyz.robinjoon.notionblog.adapter.input.web.view.BlockStyleView
import xyz.robinjoon.notionblog.adapter.input.web.view.BlockView
import xyz.robinjoon.notionblog.adapter.input.web.view.BookmarkView
import xyz.robinjoon.notionblog.adapter.input.web.view.BreadcrumbView
import xyz.robinjoon.notionblog.adapter.input.web.view.BulletedListItemView
import xyz.robinjoon.notionblog.adapter.input.web.view.CalloutView
import xyz.robinjoon.notionblog.adapter.input.web.view.ChildPostView
import xyz.robinjoon.notionblog.adapter.input.web.view.CodeLanguageView
import xyz.robinjoon.notionblog.adapter.input.web.view.CodeView
import xyz.robinjoon.notionblog.adapter.input.web.view.ColumnListView
import xyz.robinjoon.notionblog.adapter.input.web.view.ColumnView
import xyz.robinjoon.notionblog.adapter.input.web.view.ColumnWidthClass
import xyz.robinjoon.notionblog.adapter.input.web.view.DatabaseLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.DividerView
import xyz.robinjoon.notionblog.adapter.input.web.view.DocumentLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.EmbedProviderView
import xyz.robinjoon.notionblog.adapter.input.web.view.EmbedView
import xyz.robinjoon.notionblog.adapter.input.web.view.EmojiIconView
import xyz.robinjoon.notionblog.adapter.input.web.view.EquationInlineView
import xyz.robinjoon.notionblog.adapter.input.web.view.ExternalLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.HeadingLevelView
import xyz.robinjoon.notionblog.adapter.input.web.view.HeadingView
import xyz.robinjoon.notionblog.adapter.input.web.view.InlineAnnotationsView
import xyz.robinjoon.notionblog.adapter.input.web.view.InlineView
import xyz.robinjoon.notionblog.adapter.input.web.view.InternalLinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.LinkPreviewView
import xyz.robinjoon.notionblog.adapter.input.web.view.LinkView
import xyz.robinjoon.notionblog.adapter.input.web.view.ListItemView
import xyz.robinjoon.notionblog.adapter.input.web.view.ListTypeView
import xyz.robinjoon.notionblog.adapter.input.web.view.ListView
import xyz.robinjoon.notionblog.adapter.input.web.view.MediaIconView
import xyz.robinjoon.notionblog.adapter.input.web.view.MediaTypeView
import xyz.robinjoon.notionblog.adapter.input.web.view.MediaView
import xyz.robinjoon.notionblog.adapter.input.web.view.MeetingNotesStatusView
import xyz.robinjoon.notionblog.adapter.input.web.view.MeetingNotesView
import xyz.robinjoon.notionblog.adapter.input.web.view.MentionInlineView
import xyz.robinjoon.notionblog.adapter.input.web.view.MentionKindView
import xyz.robinjoon.notionblog.adapter.input.web.view.NumberedListFormatView
import xyz.robinjoon.notionblog.adapter.input.web.view.NumberedListItemView
import xyz.robinjoon.notionblog.adapter.input.web.view.ParagraphView
import xyz.robinjoon.notionblog.adapter.input.web.view.PostDocumentView
import xyz.robinjoon.notionblog.adapter.input.web.view.PostPageView
import xyz.robinjoon.notionblog.adapter.input.web.view.PresentationAssetView
import xyz.robinjoon.notionblog.adapter.input.web.view.PresentationProfileView
import xyz.robinjoon.notionblog.adapter.input.web.view.QuoteView
import xyz.robinjoon.notionblog.adapter.input.web.view.SynchronizedBlockView
import xyz.robinjoon.notionblog.adapter.input.web.view.SynchronizedOriginView
import xyz.robinjoon.notionblog.adapter.input.web.view.TabContainerView
import xyz.robinjoon.notionblog.adapter.input.web.view.TabItemView
import xyz.robinjoon.notionblog.adapter.input.web.view.TableOfContentsEntryView
import xyz.robinjoon.notionblog.adapter.input.web.view.TableOfContentsView
import xyz.robinjoon.notionblog.adapter.input.web.view.TableRowView
import xyz.robinjoon.notionblog.adapter.input.web.view.TableView
import xyz.robinjoon.notionblog.adapter.input.web.view.TemplateView
import xyz.robinjoon.notionblog.adapter.input.web.view.TextInlineView
import xyz.robinjoon.notionblog.adapter.input.web.view.TodoListItemView
import xyz.robinjoon.notionblog.adapter.input.web.view.ToggleView
import xyz.robinjoon.notionblog.adapter.input.web.view.UnsupportedView
import xyz.robinjoon.notionblog.application.model.BlogPage
import xyz.robinjoon.notionblog.application.model.LinkResolution
import xyz.robinjoon.notionblog.application.model.PresentationAssetDescriptor
import xyz.robinjoon.notionblog.domain.post.Post
import xyz.robinjoon.notionblog.domain.post.block.BlockNode
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
import xyz.robinjoon.notionblog.domain.post.block.content.TextBlockContent
import xyz.robinjoon.notionblog.domain.post.block.content.UnsupportedBlockContent
import xyz.robinjoon.notionblog.domain.post.block.inline.InlineContent
import xyz.robinjoon.notionblog.domain.post.block.inline.LinkTarget
import xyz.robinjoon.notionblog.domain.post.block.inline.MentionKind
import xyz.robinjoon.notionblog.domain.post.block.inline.TextAnnotations
import xyz.robinjoon.notionblog.domain.post.block.media.MediaSource
import xyz.robinjoon.notionblog.domain.post.block.style.Alignment
import xyz.robinjoon.notionblog.domain.post.block.style.BlockStyle
import xyz.robinjoon.notionblog.domain.post.block.style.ColorToken
import xyz.robinjoon.notionblog.domain.site.PresentationAssetRef
import xyz.robinjoon.notionblog.domain.site.PresentationColorMode
import xyz.robinjoon.notionblog.domain.site.PresentationContentWidth
import xyz.robinjoon.notionblog.domain.site.PresentationDensity
import java.net.URI
import java.time.Clock

class PostPageViewAssembler(
    private val clock: Clock,
) {
    fun assemble(page: BlogPage): PostPageView = PostPageView(
        language = page.site.metadata.languageTag,
        siteName = page.site.metadata.siteName,
        title = page.post.title,
        description = page.site.metadata.defaultDescription,
        faviconHref = page.site.metadata.favicon?.let { assetView(page.presentationAssets, it, faviconMediaTypes)?.publicPath },
        profile = profileView(page),
        styleSheets = page.presentation.styleSheets.mapNotNull { assetView(page.presentationAssets, it, styleSheetMediaTypes) },
        scripts = page.presentation.scripts.mapNotNull { assetView(page.presentationAssets, it, scriptMediaTypes) },
        header = page.header?.let { documentView(it, page.links) },
        post = documentView(page.post, page.links),
        footer = page.footer?.let { documentView(it, page.links) },
    )

    private fun documentView(post: Post, links: Map<LinkTarget.SourceDocument, LinkResolution>): PostDocumentView {
        val tableOfContents = collectHeadings(post.content.roots)
        return PostDocumentView(post.title, assembleBlocks(post.content.roots, links, tableOfContents))
    }

    private fun assembleBlocks(
        nodes: List<BlockNode>,
        links: Map<LinkTarget.SourceDocument, LinkResolution>,
        tableOfContents: List<TableOfContentsEntryView>,
    ): List<BlockView> {
        val views = mutableListOf<BlockView>()
        var index = 0
        while (index < nodes.size) {
            val node = nodes[index]
            val listType = (node.content as? ListBlockContent)?.listType()
            if (listType == null) {
                views += assembleBlock(node, links, tableOfContents)
                index += 1
                continue
            }

            val grouped = mutableListOf<ListItemView>()
            val first = node
            while (index < nodes.size && (nodes[index].content as? ListBlockContent)?.listType() == listType) {
                grouped += assembleListItem(nodes[index], links, tableOfContents)
                index += 1
            }
            val startNumber = (first.content as? ListBlockContent.NumberedItem)?.startNumber
            val numberFormat = (first.content as? ListBlockContent.NumberedItem)?.displayFormat?.view()
            views += ListView(first.id.value, listType, startNumber, numberFormat, grouped, styleView(first.style))
        }
        return views
    }

    private fun assembleBlock(
        node: BlockNode,
        links: Map<LinkTarget.SourceDocument, LinkResolution>,
        tableOfContents: List<TableOfContentsEntryView>,
    ): BlockView {
        val children = assembleBlocks(node.children, links, tableOfContents)
        val style = styleView(node.style)
        val id = node.id.value
        return when (val content = node.content) {
            is TextBlockContent.Paragraph -> ParagraphView(id, inlineViews(content.richText, links), style, children)

            is TextBlockContent.Heading -> HeadingView(id, content.level.view(), inlineViews(content.richText, links), content.isToggleable, style, children)

            is TextBlockContent.Quote -> QuoteView(id, inlineViews(content.richText, links), style, children)

            is TextBlockContent.Toggle -> ToggleView(id, inlineViews(content.richText, links), style, children)

            is TextBlockContent.Callout -> CalloutView(id, iconView(content.icon), inlineViews(content.richText, links), style, children)

            is TextBlockContent.Code -> CodeView(id, inlineViews(content.richText, links), codeLanguage(content.language), inlineViews(content.caption, links), style, children)

            is TextBlockContent.Equation -> BlockEquationView(id, content.expression, style, children)

            is ListBlockContent -> assembleListItem(node, links, tableOfContents)

            LayoutBlockContent.Divider -> DividerView(id, style, children)

            LayoutBlockContent.ColumnList -> ColumnListView(
                id,
                node.children.map { column -> assembleColumn(column, links, tableOfContents) },
                style,
            )

            is LayoutBlockContent.Column -> ColumnView(id, ColumnWidthClass.fromRatio(content.width?.ratio), style, children)

            LayoutBlockContent.TabContainer -> TabContainerView(
                id,
                node.children.map { tab -> assembleTab(tab, links, tableOfContents) },
                style,
            )

            is LayoutBlockContent.TabItem -> TabItemView(id, inlineViews(content.title, links), iconView(content.icon), style, children)

            is LayoutBlockContent.Table -> TableView(
                id,
                content.hasColumnHeader,
                content.hasRowHeader,
                node.children.map { row -> assembleTableRow(row, links, tableOfContents) },
                style,
            )

            is LayoutBlockContent.TableRow -> TableRowView(id, content.cells.map { inlineViews(it, links) }, style, children)

            is MediaBlockContent.Media -> MediaView(
                id,
                content.mediaType.view(),
                mediaUrl(content.source),
                content.fileName,
                inlineViews(content.caption, links),
                style,
                children,
            )

            is MediaBlockContent.Bookmark -> BookmarkView(id, safeExternalUrl(content.url), inlineViews(content.caption, links), style, children)

            is MediaBlockContent.LinkPreview -> LinkPreviewView(id, safeExternalUrl(content.url), style, children)

            is MediaBlockContent.Embed -> {
                val provider = content.url.embedProvider()
                EmbedView(id, provider, provider?.let { safeExternalUrl(content.url) }, inlineViews(content.caption, links), style, children)
            }

            is ReferenceBlockContent.ChildPost -> ChildPostView(id, content.title, linkView(LinkTarget.SourceDocument(content.reference, null), links), style, children)

            is ReferenceBlockContent.DocumentLink -> DocumentLinkView(
                id,
                linkView(LinkTarget.SourceDocument(content.reference, content.originalUrl), links),
                safeExternalUrl(content.originalUrl),
                style,
                children,
            )

            is ReferenceBlockContent.DatabaseLink -> DatabaseLinkView(
                id,
                linkView(LinkTarget.SourceDocument(content.reference, content.originalUrl), links),
                safeExternalUrl(content.originalUrl),
                style,
                children,
            )

            is ReferenceBlockContent.Breadcrumb -> BreadcrumbView(id, content.items.mapNotNull { linkView(it, links) }, style, children)

            ReferenceBlockContent.TableOfContents -> TableOfContentsView(id, tableOfContents, style, children)

            is ReusableBlockContent.Synchronized -> SynchronizedBlockView(
                id,
                content.origin?.let { SynchronizedOriginView(it.document.sourceId.value, it.document.externalId, it.blockExternalId) },
                style,
                children,
            )

            ReusableBlockContent.Template -> TemplateView(id, style, children)

            is SpecialBlockContent.MeetingNotes -> MeetingNotesView(
                id,
                content.title,
                content.status.view(),
                inlineViews(content.summary, links),
                content.notesReference?.let { linkView(it, links) },
                style,
                children,
            )

            is UnsupportedBlockContent -> UnsupportedView(id, content.blockType, style, children)
        }
    }

    private fun assembleListItem(
        node: BlockNode,
        links: Map<LinkTarget.SourceDocument, LinkResolution>,
        tableOfContents: List<TableOfContentsEntryView>,
    ): ListItemView {
        val style = styleView(node.style)
        val children = assembleBlocks(node.children, links, tableOfContents)
        val id = node.id.value
        return when (val content = node.content as ListBlockContent) {
            is ListBlockContent.BulletedItem -> BulletedListItemView(id, inlineViews(content.richText, links), style, children)
            is ListBlockContent.NumberedItem -> NumberedListItemView(id, inlineViews(content.richText, links), content.displayFormat.view(), style, children)
            is ListBlockContent.ToDoItem -> TodoListItemView(id, inlineViews(content.richText, links), content.checked, style, children)
        }
    }

    private fun assembleColumn(node: BlockNode, links: Map<LinkTarget.SourceDocument, LinkResolution>, toc: List<TableOfContentsEntryView>): ColumnView = assembleBlock(node, links, toc) as ColumnView

    private fun assembleTab(node: BlockNode, links: Map<LinkTarget.SourceDocument, LinkResolution>, toc: List<TableOfContentsEntryView>): TabItemView = assembleBlock(node, links, toc) as TabItemView

    private fun assembleTableRow(node: BlockNode, links: Map<LinkTarget.SourceDocument, LinkResolution>, toc: List<TableOfContentsEntryView>): TableRowView = assembleBlock(node, links, toc) as TableRowView

    private fun inlineViews(inlines: List<InlineContent>, links: Map<LinkTarget.SourceDocument, LinkResolution>): List<InlineView> = inlines.map { inline ->
        when (inline) {
            is InlineContent.Text -> TextInlineView(inline.text, annotationsView(inline.annotations), inline.link?.let { linkView(it, links) })

            is InlineContent.Equation -> EquationInlineView(inline.expression, annotationsView(inline.annotations))

            is InlineContent.Mention -> MentionInlineView(
                inline.label,
                inline.kind.view(),
                annotationsView(inline.annotations),
                inline.target?.let { linkView(it, links) },
            )
        }
    }

    private fun annotationsView(annotations: TextAnnotations): InlineAnnotationsView = InlineAnnotationsView(
        annotations.bold,
        annotations.italic,
        annotations.strikethrough,
        annotations.underline,
        annotations.code,
        colorClasses(annotations.foreground, annotations.background),
    )

    private fun linkView(target: LinkTarget, links: Map<LinkTarget.SourceDocument, LinkResolution>): LinkView? = when (target) {
        is LinkTarget.ExternalUrl -> safeExternalUrl(target.url)?.let(::ExternalLinkView)

        is LinkTarget.SourceDocument -> when (val resolution = links[target]) {
            is LinkResolution.Internal -> InternalLinkView("/posts/${resolution.postId.value}")
            is LinkResolution.External -> safeExternalUrl(resolution.url)?.let(::ExternalLinkView)
            LinkResolution.Unlinked, null -> null
        }
    }

    private fun mediaUrl(source: MediaSource): String? = when (source) {
        is MediaSource.External -> safeExternalUrl(source.url)
        is MediaSource.SourceHosted -> if (source.expiresAt?.isAfter(clock.instant()) != false) safeExternalUrl(source.url) else null
    }

    private fun iconView(icon: BlockIcon?): BlockIconView? = when (icon) {
        null -> null
        is BlockIcon.Emoji -> EmojiIconView(icon.value)
        is BlockIcon.Media -> MediaIconView(mediaUrl(icon.source))
    }

    private fun collectHeadings(nodes: List<BlockNode>): List<TableOfContentsEntryView> = buildList {
        nodes.forEach { node ->
            val heading = node.content as? TextBlockContent.Heading
            if (heading != null) {
                add(TableOfContentsEntryView(plainText(heading.richText), "#${node.id.value}", heading.level.view()))
            }
            addAll(collectHeadings(node.children))
        }
    }

    private fun plainText(inlines: List<InlineContent>): String = inlines.joinToString("") { inline ->
        when (inline) {
            is InlineContent.Text -> inline.text
            is InlineContent.Equation -> inline.expression
            is InlineContent.Mention -> inline.label
        }
    }

    private fun styleView(style: BlockStyle): BlockStyleView = BlockStyleView(
        buildList {
            colorClasses(style.foreground, style.background).forEach(::add)
            when (style.alignment) {
                Alignment.LEFT -> add("notion-align-left")
                Alignment.CENTER -> add("notion-align-center")
                Alignment.RIGHT -> add("notion-align-right")
                null -> Unit
            }
            style.width?.ratio?.let { ratio ->
                add(
                    when {
                        ratio >= 0.99 -> "notion-width-full"
                        ratio >= 0.66 -> "notion-width-wide"
                        ratio >= 0.33 -> "notion-width-standard"
                        else -> "notion-width-narrow"
                    },
                )
            }
            when (style.variant?.value) {
                "default" -> add("notion-variant-default")
                "subtle" -> add("notion-variant-subtle")
                "emphasis" -> add("notion-variant-emphasis")
                else -> Unit
            }
        },
    )

    private fun colorClasses(foreground: ColorToken?, background: ColorToken?): List<String> = buildList {
        foreground?.let { add("notion-color-${it.name.lowercase()}") }
        background?.let { add("notion-background-${it.name.lowercase()}") }
    }

    private fun profileView(page: BlogPage): PresentationProfileView = PresentationProfileView(
        listOf(
            "notion-color-mode-${page.presentation.tokens.colorMode.name.lowercase()}",
            "notion-content-width-${page.presentation.tokens.contentWidth.name.lowercase()}",
            "notion-density-${page.presentation.tokens.density.name.lowercase()}",
        ),
    )

    private fun assetView(
        assets: Map<PresentationAssetRef, PresentationAssetDescriptor>,
        reference: PresentationAssetRef,
        expectedMediaTypes: Set<String>,
    ): PresentationAssetView? {
        val descriptor = assets[reference] ?: return null
        if (descriptor.integrity != reference.integrity || descriptor.mediaType.lowercase() !in expectedMediaTypes || !descriptor.publicPath.safeAssetPath()) {
            return null
        }
        return PresentationAssetView(descriptor.publicPath, descriptor.mediaType, descriptor.integrity)
    }

    private fun String.safeAssetPath(): Boolean = startsWith('/') && !startsWith("//") && !contains('\\') && !contains('\r') && !contains('\n')

    private fun safeExternalUrl(url: URI?): String? = url?.takeIf {
        it.isAbsolute && it.host != null && it.scheme?.lowercase() in safeSchemes
    }?.toASCIIString()

    private fun URI.embedProvider(): EmbedProviderView? = when (host?.lowercase()) {
        "youtube.com", "www.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com" -> EmbedProviderView.YOUTUBE
        "vimeo.com", "www.vimeo.com", "player.vimeo.com" -> EmbedProviderView.VIMEO
        else -> null
    }

    private fun ListBlockContent.listType(): ListTypeView = when (this) {
        is ListBlockContent.BulletedItem -> ListTypeView.BULLETED
        is ListBlockContent.NumberedItem -> ListTypeView.NUMBERED
        is ListBlockContent.ToDoItem -> ListTypeView.TODO
    }

    private fun HeadingLevel.view(): HeadingLevelView = HeadingLevelView.valueOf(name)

    private fun MediaType.view(): MediaTypeView = MediaTypeView.valueOf(name)

    private fun NumberedListFormat.view(): NumberedListFormatView = NumberedListFormatView.valueOf(name)

    private fun MentionKind.view(): MentionKindView = MentionKindView.valueOf(name)

    private fun MeetingNotesStatus.view(): MeetingNotesStatusView = MeetingNotesStatusView.valueOf(name)

    private fun codeLanguage(language: String): CodeLanguageView = CodeLanguageView(
        when (language.lowercase()) {
            "kotlin", "java", "javascript", "typescript", "python", "json", "bash", "sql", "html", "css" -> "language-${language.lowercase()}"
            else -> "language-plain"
        },
    )

    private companion object {
        val safeSchemes = setOf("http", "https")
        val styleSheetMediaTypes = setOf("text/css")
        val scriptMediaTypes = setOf("application/javascript", "text/javascript")
        val faviconMediaTypes = setOf("image/x-icon", "image/png", "image/svg+xml", "image/webp")
    }
}
