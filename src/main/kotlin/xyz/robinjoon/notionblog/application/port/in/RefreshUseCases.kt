package xyz.robinjoon.notionblog.application.port.`in`

import xyz.robinjoon.notionblog.domain.model.NotionPageId

fun interface RefreshPageUseCase {
    fun refresh(pageId: NotionPageId): Boolean
}

fun interface RefreshSettingsUseCase {
    fun refresh(): SettingsRefreshResult
}

data class SettingsRefreshResult(
    val rootPageId: NotionPageId,
    val headerPageId: NotionPageId?,
    val footerPageId: NotionPageId?,
    val headJson: String,
)
