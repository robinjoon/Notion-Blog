package xyz.robinjoon.notionblog.adapter.out.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object SiteSettingsTable : Table("site_settings") {
    val id = long("id").autoIncrement()
    val settingsDataSourceId = text("settings_data_source_id").uniqueIndex()
    val rootPageId = text("root_page_id").nullable()
    val headerPageId = text("header_page_id").nullable()
    val footerPageId = text("footer_page_id").nullable()
    val headJson = jsonb("head_json", { value: String -> value }, { value -> value })
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").nullable()
    val refreshAfter = timestampWithTimeZone("refresh_after")
    val lastError = text("last_error").nullable()
    val failureCount = integer("failure_count")

    override val primaryKey = PrimaryKey(id)
}

object NotionPageTable : Table("notion_page") {
    val pageId = text("page_id")
    val title = text("title")
    val notionUrl = text("notion_url")
    val publicUrl = text("public_url").nullable()
    val visibility = varchar("visibility", 16)
    val notionLastEditedAt = timestampWithTimeZone("notion_last_edited_at").nullable()
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").nullable()
    val refreshAfter = timestampWithTimeZone("refresh_after")
    val failureCount = integer("failure_count")
    val lastError = text("last_error").nullable()

    override val primaryKey = PrimaryKey(pageId)
}

object PageSnapshotTable : Table("page_snapshot") {
    val pageId = text("page_id").references(NotionPageTable.pageId)
    val snapshotJson = jsonb("snapshot_json", { value: String -> value }, { value -> value })
    val notionLastEditedAt = timestampWithTimeZone("notion_last_edited_at")
    val capturedAt = timestampWithTimeZone("captured_at")

    override val primaryKey = PrimaryKey(pageId)
}

object PageRouteTable : Table("page_route") {
    val path = text("path")
    val pageId = text("page_id").references(NotionPageTable.pageId)
    val kind = varchar("kind", 16)
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(path)
}
