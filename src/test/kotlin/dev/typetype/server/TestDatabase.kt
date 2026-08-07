package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.AllowedPlaylistsTable
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
import dev.typetype.server.db.tables.YoutubeSessionPairingsTable
import dev.typetype.server.db.tables.YoutubeSessionsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.db.tables.UserAvatarsTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.services.AdminSettingsService
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

const val TEST_USER_ID = "test-user-id"

object TestDatabase {

    @Volatile
    private var initialized = false

    private val schemaName: String = "tt_test_${UUID.randomUUID().toString().replace("-", "")}".lowercase()

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    fun setup() {
        AdminSettingsService.clearCache()
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val (baseUrl, user, password) = runCatching {
                val c = container
                Triple(c.jdbcUrl, c.username, c.password)
            }.getOrElse {
                if (it is ContainerLaunchException) {
                    Triple(
                        firstNonBlank(System.getenv("TEST_DATABASE_URL"), "jdbc:postgresql://localhost:5432/typetype"),
                        firstNonBlank(System.getenv("TEST_DATABASE_USER"), "typetype"),
                        firstNonBlank(System.getenv("TEST_DATABASE_PASSWORD"), "typetype"),
                    )
                } else throw it
            }
            ensureSchemaExists(baseUrl = baseUrl, user = user, password = password, schema = schemaName)
            DatabaseFactory.init(withCurrentSchema(baseUrl = baseUrl, schema = schemaName), user, password)
            initialized = true
        }
    }

    private fun ensureSchemaExists(baseUrl: String, user: String, password: String, schema: String): Unit {
        DriverManager.getConnection(baseUrl, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA IF NOT EXISTS \"$schema\"")
            }
        }
    }

    private fun withCurrentSchema(baseUrl: String, schema: String): String {
        val searchPath = "$schema,public"
        return if ("?" in baseUrl) "$baseUrl&currentSchema=$searchPath" else "$baseUrl?currentSchema=$searchPath"
    }

    private fun firstNonBlank(value: String?, fallback: String): String =
        value?.takeIf { it.isNotBlank() } ?: fallback

    fun truncateAll() = transaction {
        PlaylistVideosTable.deleteAll()
        PlaylistsTable.deleteAll()
        SavedPlaylistsTable.deleteAll()
        HistoryTable.deleteAll()
        FavoritesTable.deleteAll()
        SettingsTable.deleteAll()
        SubscriptionGroupMembershipsTable.deleteAll()
        SubscriptionGroupsTable.deleteAll()
        SubscriptionsTable.deleteAll()
        WatchLaterTable.deleteAll()
        ProgressTable.deleteAll()
        SearchHistoryTable.deleteAll()
        SessionsTable.deleteAll()
        PasswordResetTable.deleteAll()
        UserAvatarsTable.deleteAll()
        UsersTable.deleteAll()
        AdminSettingsTable.deleteAll()
        AllowedChannelsTable.deleteAll()
        AllowedPlaylistsTable.deleteAll()
        BlockedChannelsTable.deleteAll()
        BlockedKeywordsTable.deleteAll()
        BlockedVideosTable.deleteAll()
        YoutubeTakeoutImportJobsTable.deleteAll()
        YoutubeTakeoutPlaylistKeysTable.deleteAll()
        YoutubeSessionPairingsTable.deleteAll()
        YoutubeSessionsTable.deleteAll()
        BugReportsTable.deleteAll()
        NotificationStatesTable.deleteAll()
        AdminSettingsService.clearCache()
    }
}
