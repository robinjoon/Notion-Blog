package xyz.robinjoon.notionblog.application.service

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.application.port.`in`.RefreshPageUseCase
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import java.util.concurrent.Executor

class PageRefreshRequesterTest {
    @Test
    fun `handled refresh failures do not escape the async worker`() {
        val pageId = NotionPageId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val requester = AsyncPageRefreshRequester(
            RefreshPageUseCase { throw IllegalStateException("token=secret-worker") },
            Executor(Runnable::run),
        )

        assertThatCode { requester.request(pageId) }.doesNotThrowAnyException()
    }
}
