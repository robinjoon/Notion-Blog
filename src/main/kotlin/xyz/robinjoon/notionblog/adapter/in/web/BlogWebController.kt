package xyz.robinjoon.notionblog.adapter.`in`.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.notionblog.adapter.out.rendering.NotionPageRenderer
import xyz.robinjoon.notionblog.application.service.LazyCollectionResult
import xyz.robinjoon.notionblog.application.service.PageAccessService
import xyz.robinjoon.notionblog.application.service.PageLookupResult
import java.net.URI

@RestController
class BlogWebController(
    private val pageAccess: PageAccessService,
    private val renderer: NotionPageRenderer,
) {
    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun root(): ResponseEntity<Any> = pageResponse(pageAccess.lookup("/"), root = true)

    @GetMapping("/{slug}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable slug: String): ResponseEntity<Any> = if (slug in reservedRootSegments) notFound() else pageResponse(pageAccess.lookup("/$slug"), root = false)

    @GetMapping("/notion/{pageId}", produces = [MediaType.TEXT_HTML_VALUE])
    fun collect(@PathVariable pageId: String): ResponseEntity<Any> = when (val result = pageAccess.collectKnownPage(pageId)) {
        is LazyCollectionResult.Redirect -> safeRedirect(HttpStatus.SEE_OTHER, result.destination)
        LazyCollectionResult.NotFound -> notFound()
    }

    private fun pageResponse(result: PageLookupResult, root: Boolean): ResponseEntity<Any> = when (result) {
        is PageLookupResult.Page -> html(renderer.render(result.title, result.blocks))
        is PageLookupResult.Redirect -> safeRedirect(HttpStatus.MOVED_PERMANENTLY, result.destination)
        PageLookupResult.NotFound -> if (root) serviceUnavailable() else notFound()
    }

    private fun safeRedirect(status: HttpStatus, destination: String): ResponseEntity<Any> = if (!isSafeInternalLocation(destination)) {
        notFound()
    } else {
        runCatching { redirect(status, destination) }.getOrElse { notFound() }
    }

    private fun redirect(status: HttpStatus, destination: String): ResponseEntity<Any> = ResponseEntity.status(status).location(URI.create(destination)).build()

    private fun html(body: String): ResponseEntity<Any> = ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body)

    private fun serviceUnavailable(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

    private fun notFound(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.NOT_FOUND).build()

    private fun isSafeInternalLocation(destination: String): Boolean = destination.isNotBlank() &&
        destination.length <= 2048 &&
        destination.startsWith('/') &&
        !destination.startsWith("//") &&
        !destination.contains('\\') &&
        !destination.contains('\r') &&
        !destination.contains('\n') &&
        runCatching { URI(destination) }.getOrNull()?.let { !it.isAbsolute && it.rawAuthority == null } == true

    private companion object {
        val reservedRootSegments = setOf("actuator", "static", "webjars", "favicon.ico")
    }
}
