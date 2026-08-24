package xyz.robinjoon.notionblog.domain.model

enum class PageVisibility {
    DISCOVERED,
    PUBLIC,
    PRIVATE,
}

enum class PageRouteKind {
    ROOT,
    CANONICAL,
    ALIAS,
}

data class PageRoute(
    val path: String,
    val pageId: NotionPageId,
    val kind: PageRouteKind,
    val active: Boolean = true,
) {
    init {
        require(path.startsWith('/')) { "a route path must start with a slash" }
        require(path == "/" || !path.endsWith('/')) { "a non-root route path cannot end with a slash" }
        require((kind == PageRouteKind.ROOT) == (path == "/")) { "a root route must use the root path" }
    }

    fun deactivate(): PageRoute = copy(active = false)
}

data class NotionPage(
    val id: NotionPageId,
    val title: String,
    val visibility: PageVisibility,
    val publicUrl: String? = null,
    val routes: List<PageRoute> = emptyList(),
) {
    init {
        require(title.isNotBlank()) { "page title must not be blank" }
        if (visibility == PageVisibility.PUBLIC) {
            require(!publicUrl.isNullOrBlank()) { "public pages require a public URL" }
        } else {
            require(publicUrl == null) { "non-public pages cannot have a public URL" }
        }
        require(routes.all { it.pageId == id }) { "all routes must belong to the page" }
        require(visibility == PageVisibility.PUBLIC || routes.none { it.active }) {
            "only public pages can have active routes"
        }
        require(routes.count { it.active && it.kind == PageRouteKind.CANONICAL } <= 1) {
            "a page can have at most one active canonical route"
        }
        require(routes.count { it.active && it.kind == PageRouteKind.ROOT } <= 1) {
            "a page can have at most one active root route"
        }
    }

    fun withdraw(): NotionPage = copy(
        visibility = PageVisibility.PRIVATE,
        publicUrl = null,
        routes = routes.map(PageRoute::deactivate),
    )
}

object PageRoutes {
    fun changeCanonical(routes: List<PageRoute>, pageId: NotionPageId, newPath: String): List<PageRoute> {
        PageRoute(newPath, pageId, PageRouteKind.CANONICAL)
        require(routes.all { it.pageId == pageId }) { "all routes must belong to the page" }
        require(routes.count { it.active && it.kind == PageRouteKind.CANONICAL } <= 1) {
            "a page can have at most one active canonical route"
        }

        var hasNewCanonical = false
        val updated = routes.map { route ->
            when {
                route.path == newPath -> {
                    hasNewCanonical = true
                    route.copy(kind = PageRouteKind.CANONICAL, active = true)
                }

                route.active && route.kind == PageRouteKind.CANONICAL -> route.copy(kind = PageRouteKind.ALIAS)

                else -> route
            }
        }

        return if (hasNewCanonical) updated else updated + PageRoute(newPath, pageId, PageRouteKind.CANONICAL)
    }
}
