package xyz.robinjoon.notionblog.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class NotionPageTest {
    private val pageId = NotionPageId("0123456789abcdef0123456789abcdef")

    @Test
    fun `public pages require their Notion public URL`() {
        assertThatIllegalArgumentException().isThrownBy {
            NotionPage(id = pageId, title = "Post", visibility = PageVisibility.PUBLIC)
        }.withMessage("public pages require a public URL")
    }

    @Test
    fun `withdrawing a page makes all routes inactive`() {
        val page = NotionPage(
            id = pageId,
            title = "Post",
            visibility = PageVisibility.PUBLIC,
            publicUrl = "https://www.notion.so/post",
            routes = listOf(PageRoute("/post", pageId, PageRouteKind.CANONICAL)),
        )

        val withdrawn = page.withdraw()

        assertThat(withdrawn.visibility).isEqualTo(PageVisibility.PRIVATE)
        assertThat(withdrawn.publicUrl).isNull()
        assertThat(withdrawn.routes).allMatch { !it.active }
    }

    @Test
    fun `a page cannot have more than one active canonical route`() {
        assertThatIllegalArgumentException().isThrownBy {
            NotionPage(
                id = pageId,
                title = "Post",
                visibility = PageVisibility.PUBLIC,
                publicUrl = "https://www.notion.so/post",
                routes = listOf(
                    PageRoute("/post", pageId, PageRouteKind.CANONICAL),
                    PageRoute("/post-new", pageId, PageRouteKind.CANONICAL),
                ),
            )
        }.withMessage("a page can have at most one active canonical route")
    }

    @Test
    fun `changing the canonical route retains the old path as an alias`() {
        assertThat(
            PageRoutes.changeCanonical(
                routes = listOf(PageRoute("/first", pageId, PageRouteKind.CANONICAL)),
                pageId = pageId,
                newPath = "/second",
            ),
        ).containsExactly(
            PageRoute("/first", pageId, PageRouteKind.ALIAS),
            PageRoute("/second", pageId, PageRouteKind.CANONICAL),
        )
    }

    @Test
    fun `root route is restricted to the root path`() {
        assertThatIllegalArgumentException().isThrownBy {
            PageRoute("/post", pageId, PageRouteKind.ROOT)
        }.withMessage("a root route must use the root path")
    }
}
