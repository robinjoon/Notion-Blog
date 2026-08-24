package xyz.robinjoon.notionblog.adapter.out.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.application.port.out.persistence.PublicPageSnapshotWrite
import xyz.robinjoon.notionblog.application.port.out.persistence.ResolvedRoute
import xyz.robinjoon.notionblog.domain.model.NotionPageId
import xyz.robinjoon.notionblog.domain.model.PageRoute
import xyz.robinjoon.notionblog.domain.model.PageRouteKind
import java.sql.Connection
import java.sql.DriverManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceSchemaIntegrationTest {
    private val database = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    private lateinit var exposedDatabase: Database

    @BeforeAll
    fun migrate() {
        database.start()
        Flyway.configure()
            .dataSource(database.jdbcUrl, database.username, database.password)
            .locations("classpath:db/migration")
            .connectRetries(5)
            .connectRetriesInterval(1)
            .load()
            .migrate()
        exposedDatabase = Database.connect(database.jdbcUrl, driver = "org.postgresql.Driver", user = database.username, password = database.password)
    }

    @AfterAll
    fun stopDatabase() {
        database.stop()
    }

    @BeforeEach
    fun clearDatabase() {
        connection { connection ->
            connection.createStatement().use { statement ->
                statement.execute("truncate table page_route, page_snapshot, site_settings, notion_page")
            }
        }
    }

    @Test
    fun `migrates normalized snapshots as jsonb and all instants as timestamptz`() {
        connection { connection ->
            val columns = connection.prepareStatement(
                """
                select column_name, data_type, udt_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in ('site_settings', 'notion_page', 'page_snapshot', 'page_route')
                """.trimIndent(),
            ).executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString("column_name"), rows.getString("data_type") to rows.getString("udt_name"))
                    }
                }
            }

            assertThat(columns["head_json"]).isEqualTo("jsonb" to "jsonb")
            assertThat(columns["snapshot_json"]).isEqualTo("jsonb" to "jsonb")
            assertThat(columns["refresh_after"]?.first).isEqualTo("timestamp with time zone")
            assertThat(columns["captured_at"]?.first).isEqualTo("timestamp with time zone")
            assertThat(PageSnapshotTable.tableName).isEqualTo("page_snapshot")
            transaction(exposedDatabase) {
                assertThat(PageSnapshotTable.snapshotJson.columnType.sqlType()).isEqualTo("JSONB")
            }
        }
    }

    @Test
    fun `enforces path and active route uniqueness with page snapshot foreign keys`() {
        connection { connection ->
            insertPage(connection, PAGE_A)
            insertPage(connection, PAGE_B)
            insertRoute(connection, "/first", PAGE_A, "CANONICAL")

            assertThatThrownBy { insertRoute(connection, "/second", PAGE_A, "CANONICAL") }
                .hasMessageContaining("page_route_active_canonical_per_page")
            assertThatThrownBy { insertRoute(connection, "/first", PAGE_B, "CANONICAL") }
                .hasMessageContaining("page_route_pkey")
            insertRoute(connection, "/", PAGE_A, "ROOT")
            assertThatThrownBy { insertRoute(connection, "/other-root", PAGE_B, "ROOT") }
                .hasMessageContaining("page_route_root_path_check")
            assertThatThrownBy { insertSnapshot(connection, PAGE_MISSING) }
                .hasMessageContaining("page_snapshot_page_id_fkey")
        }
    }

    @Test
    fun `rolls back a private transition and route deactivation when the transaction fails`() {
        connection { connection ->
            insertPage(connection, PAGE_A)
            insertRoute(connection, "/post", PAGE_A, "CANONICAL")

            connection.autoCommit = false
            try {
                connection.prepareStatement("update notion_page set visibility = 'PRIVATE', public_url = null where page_id = ?")
                    .use { statement ->
                        statement.setString(1, PAGE_A)
                        statement.executeUpdate()
                    }
                connection.prepareStatement("update page_route set active = false where page_id = ?")
                    .use { statement ->
                        statement.setString(1, PAGE_A)
                        statement.executeUpdate()
                    }
                assertThatThrownBy { insertRoute(connection, "/post", PAGE_A, "ALIAS") }
                connection.rollback()
            } finally {
                connection.autoCommit = true
            }

            connection.prepareStatement(
                "select p.visibility, r.active from notion_page p join page_route r on r.page_id = p.page_id where p.page_id = ?",
            ).use { statement ->
                statement.setString(1, PAGE_A)
                statement.executeQuery().use { row ->
                    row.next()
                    assertThat(row.getString("visibility")).isEqualTo("PUBLIC")
                    assertThat(row.getBoolean("active")).isTrue()
                }
            }
        }
    }

    @Test
    fun `persists and resolves a public snapshot then hides it when made private`() {
        val pageId = NotionPageId(PAGE_D)
        val adapter = ExposedBlogPersistenceAdapter()
        val capturedAt = java.time.Instant.parse("2026-07-01T00:00:00Z")

        transaction(exposedDatabase) {
            adapter.recordDiscoveredPage(pageId, capturedAt)
            assertThat(adapter.findDuePageIds(capturedAt, 10)).contains(pageId)

            adapter.savePublicPageSnapshot(
                PublicPageSnapshotWrite(
                    pageId = pageId,
                    title = "Persisted post",
                    notionUrl = "https://www.notion.so/persisted-post",
                    publicUrl = "https://www.notion.so/persisted-post",
                    notionLastEditedAt = capturedAt,
                    syncedAt = capturedAt,
                    refreshAfter = capturedAt.plusSeconds(900),
                    snapshotJson = "{\"blocks\":[]}",
                    capturedAt = capturedAt,
                    routes = listOf(PageRoute("/persisted-post", pageId, PageRouteKind.CANONICAL)),
                ),
            )

            assertThat(adapter.resolveRoute("/persisted-post"))
                .isEqualTo(ResolvedRoute.Page(pageId, "/persisted-post"))
            assertThat(JsonMapper.builder().build().readTree(adapter.findPublicPageSnapshot(pageId)?.snapshotJson))
                .isEqualTo(JsonMapper.builder().build().readTree("{\"blocks\":[]}"))

            adapter.makePagePrivate(pageId, capturedAt.plusSeconds(300))

            assertThat(adapter.resolveRoute("/persisted-post")).isNull()
            assertThat(adapter.findPublicPageSnapshot(pageId)).isNull()
        }
    }

    private fun connection(block: (Connection) -> Unit) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use(block)
    }

    private fun insertPage(connection: Connection, pageId: String) {
        connection.prepareStatement(
            """
            insert into notion_page (page_id, title, notion_url, public_url, visibility, refresh_after, failure_count)
            values (?, 'Post', 'https://www.notion.so/post', 'https://www.notion.so/post', 'PUBLIC', now(), 0)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, pageId)
            statement.executeUpdate()
        }
    }

    private fun insertRoute(connection: Connection, path: String, pageId: String, kind: String) {
        connection.prepareStatement("insert into page_route (path, page_id, kind, active, created_at) values (?, ?, ?, true, now())").use { statement ->
            statement.setString(1, path)
            statement.setString(2, pageId)
            statement.setString(3, kind)
            statement.executeUpdate()
        }
    }

    private fun insertSnapshot(connection: Connection, pageId: String) {
        connection.prepareStatement(
            "insert into page_snapshot (page_id, snapshot_json, notion_last_edited_at, captured_at) values (?, '{}'::jsonb, now(), now())",
        ).use { statement ->
            statement.setString(1, pageId)
            statement.executeUpdate()
        }
    }

    private companion object {
        const val PAGE_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val PAGE_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val PAGE_MISSING = "cccccccccccccccccccccccccccccccc"
        const val PAGE_D = "dddddddddddddddddddddddddddddddd"
    }
}
