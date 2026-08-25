package xyz.robinjoon.notionblog.adapter.output.persistence.schema

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
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.PostSnapshotTable
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.PostTable
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.PresentationProfileTable
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.PublicationMemberTable
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.SiteConfigurationTable
import xyz.robinjoon.notionblog.adapter.output.persistence.exposed.table.SyncStateTable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TargetPersistenceSchemaIntegrationTest {
    private val database = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    private val jsonMapper = JsonMapper.builder().build()
    private lateinit var exposedDatabase: Database

    @BeforeAll
    fun migrate() {
        database.start()
        Flyway.configure()
            .dataSource(database.jdbcUrl, database.username, database.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        exposedDatabase = Database.connect(database.jdbcUrl, driver = "org.postgresql.Driver", user = database.username, password = database.password)
    }

    @AfterAll
    fun stopDatabase() {
        database.stop()
    }

    @BeforeEach
    fun clearTargetTables() {
        connection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "truncate table site_configuration, publication_member, publication_revision, publication, post_availability, post_snapshot, " +
                        "post_source_binding, post cascade",
                )
            }
            connection.prepareStatement(
                "delete from sync_state where target_kind <> 'SITE_CONFIGURATION' or target_key <> 'singleton'",
            ).use { statement ->
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "delete from presentation_profile_asset where presentation_profile_id <> ?",
            ).use { statement ->
                statement.setObject(1, DEFAULT_PROFILE_ID)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "delete from presentation_profile where presentation_profile_id <> ?",
            ).use { statement ->
                statement.setObject(1, DEFAULT_PROFILE_ID)
                statement.executeUpdate()
            }
        }
    }

    @Test
    fun `seeds the immutable default presentation profile with catalog-matched ordered assets`() {
        connection { connection ->
            connection.prepareStatement(
                "select profile_key, version, token_json, is_current, created_at from presentation_profile " +
                    "where presentation_profile_id = ?",
            ).use { statement ->
                statement.setObject(1, DEFAULT_PROFILE_ID)
                statement.executeQuery().use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString("profile_key")).isEqualTo("notion-default")
                    assertThat(rows.getLong("version")).isEqualTo(1)
                    assertThat(jsonMapper.readTree(rows.getString("token_json"))).isEqualTo(
                        jsonMapper.readTree("""{"colorMode":"SYSTEM","contentWidth":"STANDARD","density":"COMFORTABLE"}"""),
                    )
                    assertThat(rows.getBoolean("is_current")).isTrue()
                    assertThat(rows.getObject("created_at", OffsetDateTime::class.java).toInstant())
                        .isEqualTo(Instant.parse("2026-08-25T00:00:00Z"))
                    assertThat(rows.next()).isFalse()
                }
            }

            val assets = connection.prepareStatement(
                "select asset_kind, asset_key, asset_version, integrity, position from presentation_profile_asset " +
                    "where presentation_profile_id = ? and presentation_profile_version = 1 order by asset_kind",
            ).use { statement ->
                statement.setObject(1, DEFAULT_PROFILE_ID)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                SeedAsset(
                                    rows.getString("asset_kind"),
                                    rows.getString("asset_key"),
                                    rows.getLong("asset_version"),
                                    rows.getString("integrity"),
                                    rows.getInt("position"),
                                ),
                            )
                        }
                    }
                }
            }

            assertThat(assets).containsExactlyInAnyOrder(
                SeedAsset(
                    "STYLE_SHEET",
                    "notion-core",
                    1,
                    "sha384-V763UM2y9iSN6rUXr+H4a3GeowrAJqJ53QPBQoJQ9/W3UVee9kfeg7pO3tJJ7V/T",
                    0,
                ),
                SeedAsset(
                    "SCRIPT",
                    "notion-tabs",
                    1,
                    "sha384-VucbIMH0dIpFjnUI6nyjosBUX+cUDRo82zmVz+TihzIdd4C9WwKtpQ1i06jBFgUy",
                    0,
                ),
            )

            connection.prepareStatement(
                "select last_success_at, refresh_after, failure_count, last_error_kind, refresh_after <= now() as is_due " +
                    "from sync_state where target_kind = 'SITE_CONFIGURATION' and target_key = 'singleton'",
            ).executeQuery().use { rows ->
                assertThat(rows.next()).isTrue()
                assertThat(rows.getObject("last_success_at")).isNull()
                assertThat(rows.getObject("refresh_after", OffsetDateTime::class.java).toInstant())
                    .isEqualTo(Instant.EPOCH)
                assertThat(rows.getInt("failure_count")).isZero()
                assertThat(rows.getObject("last_error_kind")).isNull()
                assertThat(rows.getBoolean("is_due")).isTrue()
                assertThat(rows.next()).isFalse()
            }
        }
    }

    @Test
    fun `migrates every target table with PostgreSQL types and matching Exposed declarations`() {
        connection { connection ->
            val tables = connection.prepareStatement(
                "select table_name from information_schema.tables where table_schema = 'public' and table_name in " +
                    "('post', 'post_source_binding', 'post_snapshot', 'post_availability', 'publication', " +
                    "'publication_revision', 'publication_member', 'site_configuration', 'presentation_profile', " +
                    "'presentation_profile_asset', 'sync_state')",
            ).executeQuery().use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("table_name"))
                }
            }

            assertThat(tables).containsExactlyInAnyOrder(
                "post",
                "post_source_binding",
                "post_snapshot",
                "post_availability",
                "publication",
                "publication_revision",
                "publication_member",
                "site_configuration",
                "presentation_profile",
                "presentation_profile_asset",
                "sync_state",
            )
            assertThat(columnType(connection, "post", "post_id")).isEqualTo("uuid")
            assertThat(columnType(connection, "post_snapshot", "snapshot_json")).isEqualTo("jsonb")
            assertThat(columnType(connection, "site_configuration", "site_id")).isEqualTo("smallint")
            assertThat(columnType(connection, "publication_member", "depth")).isEqualTo("integer")
            assertThat(columnType(connection, "sync_state", "refresh_after")).isEqualTo("timestamp with time zone")

            transaction(exposedDatabase) {
                assertThat(PostTable.tableName).isEqualTo("post")
                assertThat(PostTable.postId.columnType.sqlType()).isEqualToIgnoringCase("UUID")
                assertThat(PostSnapshotTable.snapshotJson.columnType.sqlType()).isEqualToIgnoringCase("JSONB")
                assertThat(PublicationMemberTable.depth.columnType.sqlType()).isEqualToIgnoringCase("INT")
                assertThat(SiteConfigurationTable.siteId.columnType.sqlType()).isEqualToIgnoringCase("SMALLINT")
                assertThat(PresentationProfileTable.isCurrent.columnType.sqlType()).isEqualToIgnoringCase("BOOLEAN")
                assertThat(SyncStateTable.refreshAfter.columnType.sqlType()).isEqualToIgnoringCase("TIMESTAMP WITH TIME ZONE")
            }
        }
    }

    @Test
    fun `does not create legacy PoC tables on a clean database`() {
        connection { connection ->
            val legacyTables = connection.prepareStatement(
                "select table_name from information_schema.tables where table_schema = 'public' and table_name in " +
                    "('notion_page', 'site_settings', 'page_snapshot', 'page_route')",
            ).executeQuery().use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("table_name"))
                }
            }

            assertThat(legacyTables).isEmpty()
        }
    }

    @Test
    fun `enforces source binding publication ownership member parent and root constraints`() {
        connection { connection ->
            val firstPost = UUID.randomUUID()
            val secondPost = UUID.randomUUID()
            val thirdPost = UUID.randomUUID()
            val firstPublication = UUID.randomUUID()
            val secondPublication = UUID.randomUUID()
            val firstRevision = UUID.randomUUID()
            val secondRevision = UUID.randomUUID()

            insertPost(connection, firstPost)
            insertPost(connection, secondPost)
            insertPost(connection, thirdPost)
            insertBinding(connection, "notion-main", "external-1", firstPost)
            assertThatThrownBy { insertBinding(connection, "notion-main", "external-1", secondPost) }
                .hasMessageContaining("post_source_binding_pkey")
            assertThatThrownBy { insertBinding(connection, "notion-main", "external-2", firstPost) }
                .hasMessageContaining("post_source_binding_post_id_key")

            insertPublication(connection, firstPublication)
            insertPublication(connection, secondPublication)
            insertRevision(connection, firstRevision, firstPublication, "STAGING")
            insertRevision(connection, secondRevision, secondPublication, "STAGING")
            insertRevision(connection, UUID.randomUUID(), firstPublication, "ACTIVE", activated = true)
            assertThatThrownBy { insertRevision(connection, UUID.randomUUID(), firstPublication, "ACTIVE", activated = true) }
                .hasMessageContaining("publication_revision_one_active_per_publication")
            activatePublication(connection, firstPublication, firstPost, firstRevision)
            assertThatThrownBy { activatePublication(connection, secondPublication, secondPost, firstRevision) }
                .hasMessageContaining("publication_active_revision_ownership_fkey")

            insertMember(connection, firstRevision, firstPost, null, 0)
            assertThatThrownBy { insertMember(connection, firstRevision, secondPost, null, 0) }
                .hasMessageContaining("publication_member_one_root_per_revision")
            assertThatThrownBy { insertMember(connection, firstRevision, secondPost, thirdPost, 1) }
                .hasMessageContaining("publication_member_parent_in_same_revision_fkey")
            assertThatThrownBy { insertMember(connection, firstRevision, secondPost, firstPost, -1) }
                .hasMessageContaining("publication_member")
        }
    }

    @Test
    fun `enforces profile version references current profile uniqueness and sync enum values`() {
        connection { connection ->
            val publicationId = UUID.randomUUID()
            val profileId = UUID.randomUUID()

            insertPublication(connection, publicationId)
            insertProfile(connection, profileId, "default", 1, true)
            assertThatThrownBy { insertProfile(connection, UUID.randomUUID(), "default", 2, true) }
                .hasMessageContaining("presentation_profile_one_current_per_key")
            insertProfile(connection, profileId, "default", 2, false)
            assertThatThrownBy { insertSiteConfiguration(connection, 1.toShort(), publicationId, profileId, 99) }
                .hasMessageContaining("site_configuration_presentation_profile_fkey")
            insertSiteConfiguration(connection, 1.toShort(), publicationId, profileId, 1)
            assertThatThrownBy { insertSiteConfiguration(connection, 2.toShort(), publicationId, profileId, 2) }
                .hasMessageContaining("site_configuration_singleton_check")

            assertThatThrownBy {
                connection.prepareStatement(
                    "insert into sync_state (target_kind, target_key, refresh_after, failure_count, last_error_kind) " +
                        "values ('POST', 'post-1', now(), 1, 'HTTP_429')",
                ).use { it.executeUpdate() }
            }.hasMessageContaining("sync_state_last_error_kind_check")
            assertThatThrownBy {
                connection.prepareStatement(
                    "insert into sync_state (target_kind, target_key, refresh_after, failure_count, last_error_kind) " +
                        "values ('POST', 'post-2', now(), 0, 'MAPPING')",
                ).use { it.executeUpdate() }
            }.hasMessageContaining("sync_state_failure_error_pair_check")
            assertThatThrownBy {
                connection.prepareStatement(
                    "insert into post_availability (post_id, status, confirmed_at) values (?, 'private', now())",
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.executeUpdate()
                }
            }.hasMessageContaining("post_availability_status_check")
        }
    }

    private fun columnType(connection: Connection, table: String, column: String): String = connection.prepareStatement(
        "select data_type from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, column)
        statement.executeQuery().use { rows ->
            check(rows.next()) { "column $table.$column was not found" }
            rows.getString("data_type")
        }
    }

    private fun insertPost(connection: Connection, postId: UUID) {
        connection.prepareStatement("insert into post (post_id, title, created_at, updated_at) values (?, 'Post', now(), now())").use { statement ->
            statement.setObject(1, postId)
            statement.executeUpdate()
        }
    }

    private fun insertBinding(connection: Connection, sourceId: String, externalId: String, postId: UUID) {
        connection.prepareStatement("insert into post_source_binding (source_id, external_id, post_id) values (?, ?, ?)").use { statement ->
            statement.setString(1, sourceId)
            statement.setString(2, externalId)
            statement.setObject(3, postId)
            statement.executeUpdate()
        }
    }

    private fun insertPublication(connection: Connection, publicationId: UUID) {
        connection.prepareStatement("insert into publication (publication_id) values (?)").use { statement ->
            statement.setObject(1, publicationId)
            statement.executeUpdate()
        }
    }

    private fun insertRevision(
        connection: Connection,
        revisionId: UUID,
        publicationId: UUID,
        state: String,
        activated: Boolean = false,
    ) {
        connection.prepareStatement(
            "insert into publication_revision (revision_id, publication_id, state, started_at, activated_at) values (?, ?, ?, now(), ?) ",
        ).use { statement ->
            statement.setObject(1, revisionId)
            statement.setObject(2, publicationId)
            statement.setString(3, state)
            if (activated) {
                statement.setObject(4, OffsetDateTime.now())
            } else {
                statement.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE)
            }
            statement.executeUpdate()
        }
    }

    private fun activatePublication(connection: Connection, publicationId: UUID, rootPostId: UUID, revisionId: UUID) {
        connection.prepareStatement("update publication set root_post_id = ?, active_revision_id = ? where publication_id = ?").use { statement ->
            statement.setObject(1, rootPostId)
            statement.setObject(2, revisionId)
            statement.setObject(3, publicationId)
            statement.executeUpdate()
        }
    }

    private fun insertMember(connection: Connection, revisionId: UUID, postId: UUID, parentPostId: UUID?, depth: Int) {
        connection.prepareStatement(
            "insert into publication_member (revision_id, post_id, parent_post_id, depth) values (?, ?, ?, ?)",
        ).use { statement ->
            statement.setObject(1, revisionId)
            statement.setObject(2, postId)
            statement.setObject(3, parentPostId)
            statement.setInt(4, depth)
            statement.executeUpdate()
        }
    }

    private fun insertProfile(connection: Connection, profileId: UUID, key: String, version: Long, isCurrent: Boolean) {
        connection.prepareStatement(
            "insert into presentation_profile (presentation_profile_id, profile_key, version, token_json, is_current, created_at) " +
                "values (?, ?, ?, '{}'::jsonb, ?, now())",
        ).use { statement ->
            statement.setObject(1, profileId)
            statement.setString(2, key)
            statement.setLong(3, version)
            statement.setBoolean(4, isCurrent)
            statement.executeUpdate()
        }
    }

    private fun insertSiteConfiguration(
        connection: Connection,
        siteId: Short,
        publicationId: UUID,
        profileId: UUID,
        profileVersion: Long,
    ) {
        connection.prepareStatement(
            "insert into site_configuration (site_id, publication_id, root_source_id, root_external_id, metadata_json, " +
                "presentation_profile_id, presentation_profile_version, synced_at) values (?, ?, 'notion-main', 'root', " +
                "'{}'::jsonb, ?, ?, now())",
        ).use { statement ->
            statement.setShort(1, siteId)
            statement.setObject(2, publicationId)
            statement.setObject(3, profileId)
            statement.setLong(4, profileVersion)
            statement.executeUpdate()
        }
    }

    private fun connection(block: (Connection) -> Unit) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use(block)
    }

    private data class SeedAsset(
        val kind: String,
        val key: String,
        val version: Long,
        val integrity: String,
        val position: Int,
    )

    private companion object {
        val DEFAULT_PROFILE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
