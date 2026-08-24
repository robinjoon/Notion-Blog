package xyz.robinjoon.notionblog.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import xyz.robinjoon.notionblog.adapter.out.rendering.NotionPageRenderer
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.application.port.out.persistence.BlogPersistencePort
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshot
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.application.port.out.persistence.SiteSettingsWrite
import xyz.robinjoon.notionblog.application.service.PageAccessService
import xyz.robinjoon.notionblog.application.service.PageRefreshRequester
import xyz.robinjoon.notionblog.domain.model.NotionBlock
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.ParagraphBlock
import xyz.robinjoon.notionblog.domain.model.RichText
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BlogWebControllerTest {
    private val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    private val now = Instant.parse("2026-08-23T00:00:00Z")
    private lateinit var persistence: FakePersistence
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        persistence = FakePersistence(pageId)
        mockMvc = MockMvcBuilders.standaloneSetup(controller()).build()
    }

    @Test
    fun `root page renders a complete escaped html document`() {
        persistence.routes["/"] = ResolvedRoute.Page(pageId, "/")
        persistence.snapshots[pageId] = snapshot("<Unsafe title>")

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<!doctype html>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<meta charset=\"UTF-8\">")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"viewport\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/notion.css")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;Unsafe title&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<Unsafe title>"))))
    }

    @Test
    fun `root redirect is permanent and root not found means initialization`() {
        persistence.routes["/"] = ResolvedRoute.Redirect("/home")
        mockMvc.perform(get("/"))
            .andExpect(status().isMovedPermanently)
            .andExpect(header().string("Location", "/home"))

        persistence.routes["/"] = null
        mockMvc.perform(get("/"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `slug page renders or redirects aliases and missing pages are not found`() {
        persistence.routes["/post"] = ResolvedRoute.Page(pageId, "/post")
        persistence.snapshots[pageId] = snapshot("Post")
        mockMvc.perform(get("/post"))
            .andExpect(status().isOk)

        persistence.routes["/old"] = ResolvedRoute.Redirect("/post")
        mockMvc.perform(get("/old"))
            .andExpect(status().isMovedPermanently)
            .andExpect(header().string("Location", "/post"))

        persistence.routes["/missing"] = null
        mockMvc.perform(get("/missing"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `known notion page uses see other and unknown page is not found`() {
        persistence.known += pageId
        persistence.routesForPage[pageId] = listOf(PageRoute("/post", pageId, xyz.robinjoon.notionblog.domain.model.PageRouteKind.CANONICAL))

        mockMvc.perform(get("/notion/${pageId.value}"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/post"))

        mockMvc.perform(get("/notion/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `redirects never accept external or header injection destinations`() {
        persistence.routes["/"] = ResolvedRoute.Redirect("https://evil.example")
        mockMvc.perform(get("/"))
            .andExpect(status().isNotFound)
            .andExpect(header().doesNotExist("Location"))

        persistence.routes["/post"] = ResolvedRoute.Redirect("/safe\r\nX-Injected: yes")
        mockMvc.perform(get("/post"))
            .andExpect(status().isNotFound)
            .andExpect(header().doesNotExist("Location"))
    }

    @Test
    fun `static and actuator paths do not fall through to the page lookup`() {
        mockMvc.perform(get("/static/app.css"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/static"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator"))
            .andExpect(status().isNotFound)
        assertThat(persistence.lookedUpPaths).doesNotContain("/static", "/actuator")
    }

    private fun controller(): BlogWebController {
        val access = PageAccessService(
            persistence = persistence,
            pageRefresh = PageRefreshRequester { },
            clock = Clock.fixed(now, ZoneOffset.UTC),
            snapshotCodec = Codec,
            lazyPageRefresh = RefreshPageUseCase { true },
        )
        val resolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = org.thymeleaf.templatemode.TemplateMode.HTML
            characterEncoding = "UTF-8"
            isCacheable = false
        }
        val renderer = NotionPageRenderer(SpringTemplateEngine().apply { setTemplateResolver(resolver) })
        return BlogWebController(access, renderer)
    }

    private fun snapshot(title: String): PublicPageSnapshot = PublicPageSnapshot(
        pageId = pageId,
        title = title,
        snapshotJson = "ignored",
        notionLastEditedAt = now,
        capturedAt = now,
        refreshAfter = now.plusSeconds(900),
    )

    private object Codec : PageSnapshotCodec {
        override fun encode(blocks: List<NotionBlock>): String = "ignored"
        override fun decode(snapshotJson: String): List<NotionBlock> = listOf(ParagraphBlock("p", listOf(RichText("Body"))))
    }

    private class FakePersistence(
        private val pageId: NotionPageId,
    ) : BlogPersistencePort {
        val routes = mutableMapOf<String, ResolvedRoute?>()
        val snapshots = mutableMapOf<NotionPageId, PublicPageSnapshot>()
        val routesForPage = mutableMapOf<NotionPageId, List<PageRoute>>()
        val known = mutableSetOf<NotionPageId>()
        val lookedUpPaths = mutableListOf<String>()

        override fun resolveRoute(path: String): ResolvedRoute? {
            lookedUpPaths += path
            return routes[path]
        }

        override fun findPublicPageSnapshot(pageId: NotionPageId): PublicPageSnapshot? = snapshots[pageId]
        override fun findRoutesForPage(pageId: NotionPageId): List<PageRoute> = routesForPage[pageId].orEmpty()
        override fun isKnownPage(pageId: NotionPageId): Boolean = pageId in known
        override fun findDuePageIds(now: Instant, limit: Int): List<NotionPageId> = emptyList()
        override fun findDueSettingsDataSourceIds(now: Instant, limit: Int): List<String> = emptyList()
        override fun recordDiscoveredPage(pageId: NotionPageId, refreshAfter: Instant) = Unit
        override fun savePublicPageSnapshot(snapshot: PublicPageSnapshotWrite) = Unit
        override fun saveSettings(settings: SiteSettingsWrite) = Unit
        override fun makePagePrivate(pageId: NotionPageId, refreshAfter: Instant, lastError: String?) = Unit
        override fun touchPublicPage(pageId: NotionPageId, syncedAt: Instant, refreshAfter: Instant) = Unit
    }
}
