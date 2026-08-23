package xyz.robinjoon.notionblog.application.port.out.notion

import xyz.robinjoon.notionblog.domain.model.NotionBlock
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import java.time.Instant

interface NotionGateway {
    fun retrievePage(pageId: NotionPageId): NotionPageMetadata

    fun retrievePageContent(pageId: NotionPageId): NotionPageContent

    fun querySettingsDataSource(dataSourceId: String): List<NotionSettingsRow>
}

data class NotionPageMetadata(
    val id: NotionPageId,
    val title: String,
    val notionUrl: String,
    val publicUrl: String?,
    val lastEditedAt: Instant,
)

data class NotionPageContent(
    val blocks: List<NotionBlock>,
    val linkedPageIds: List<NotionPageId>,
)

data class NotionSettingsRow(
    val key: String,
    val kind: NotionSettingKind,
    val enabled: Boolean,
    val page: String,
    val data: String,
)

enum class NotionSettingKind {
    PAGE,
    BLOCKS,
    HEAD,
}
