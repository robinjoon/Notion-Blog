package xyz.robinjoon.notionblog.adapter.input.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice
internal class WebExceptionHandler {
    @ExceptionHandler(MalformedPostIdException::class)
    internal fun handle(@Suppress("UNUSED_PARAMETER") exception: MalformedPostIdException): ModelAndView = ModelAndView("error/blog-not-found").apply {
        status = HttpStatus.NOT_FOUND
    }
}

internal class MalformedPostIdException : RuntimeException()
