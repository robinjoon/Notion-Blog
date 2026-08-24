package xyz.robinjoon.notionblog.application.service

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.domain.model.NotionPageId

fun interface PageRefreshRequester {
    fun request(pageId: NotionPageId)
}

class AsyncPageRefreshRequester(
    private val refresh: RefreshPageUseCase,
    private val executor: Executor,
) : PageRefreshRequester {
    override fun request(pageId: NotionPageId) {
        try {
            executor.execute {
                try {
                    refresh.refresh(pageId)
                } catch (_: RuntimeException) {
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }
}
