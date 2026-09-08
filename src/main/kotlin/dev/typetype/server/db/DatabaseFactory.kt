package dev.typetype.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedKeywordsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SavedPlaylistsTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.db.tables.UserAvatarsTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.db.tables.AllowedChannelsTable
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.db.tables.ProfileAccountsTable
import dev.typetype.server.db.tables.RecommendationEventsTable
import dev.typetype.server.db.tables.RecommendationFeedHistoryTable
import dev.typetype.server.db.tables.RecommendationFeedbackTable
import dev.typetype.server.db.tables.RecommendationOnboardingPreferencesTable
import dev.typetype.server.db.tables.RecommendationOnboardingStateTable
import dev.typetype.server.db.tables.RssFeedChannelsTable
import dev.typetype.server.db.tables.RssFeedServicesTable
import dev.typetype.server.db.tables.RssFeedsTable
import dev.typetype.server.db.tables.RssUserPoliciesTable
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
import dev.typetype.server.db.tables.YoutubeSessionPairingsTable
import dev.typetype.server.db.tables.YoutubeSessionsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    private const val POOL_SIZE = 10
    private val queryDispatcher = Dispatchers.IO.limitedParallelism(POOL_SIZE, "database")

    fun init(url: String, user: String, password: String) {
        val dbPassword = password
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = POOL_SIZE
            minimumIdle = 2
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.create(
                UsersTable,
                ProfileAccountsTable,
                UserAvatarsTable,
                SessionsTable,
                AdminSettingsTable,
                HistoryTable,
                SubscriptionsTable,
                SubscriptionGroupsTable,
                SubscriptionGroupMembershipsTable,
                PlaylistsTable,
                PlaylistVideosTable,
                WatchLaterTable,
                ProgressTable,
                SavedPlaylistsTable,
                FavoritesTable,
                SettingsTable,
                AllowedChannelsTable,
                dev.typetype.server.db.tables.AllowedPlaylistsTable,
                SearchHistoryTable,
                BlockedChannelsTable,
                BlockedKeywordsTable,
                BlockedVideosTable,
                PasswordResetTable,
                YoutubeTakeoutImportJobsTable,
                YoutubeTakeoutPlaylistKeysTable,
                YoutubeSessionsTable,
                YoutubeSessionPairingsTable,
                BugReportsTable,
                NotificationStatesTable,
                UserChannelInterestTable,
                UserTopicInterestTable,
                RecommendationEventsTable,
                RecommendationFeedHistoryTable,
                RecommendationFeedbackTable,
                RecommendationOnboardingPreferencesTable,
                RecommendationOnboardingStateTable,
                RssFeedsTable,
                RssFeedChannelsTable,
                RssFeedServicesTable,
                RssUserPoliciesTable,
            )
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS name TEXT")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS thumbnail_url TEXT")
            SettingsSchemaMigrations.apply()
            exec("ALTER TABLE history ADD COLUMN IF NOT EXISTS channel_avatar TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE history ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE progress ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE search_history ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlists ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS channel_name TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS channel_url TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS channel_avatar TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS added_at BIGINT NOT NULL DEFAULT 0")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS published_at BIGINT NOT NULL DEFAULT -1")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS caption_styles TEXT NOT NULL DEFAULT '{}'")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS access_mode TEXT NOT NULL DEFAULT 'unrestricted'")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_enabled BOOLEAN NOT NULL DEFAULT FALSE")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_public_base_url TEXT")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_max_feeds_per_user INTEGER NOT NULL DEFAULT 10")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_max_items INTEGER NOT NULL DEFAULT 50")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_minimum_poll_minutes INTEGER NOT NULL DEFAULT 5")
            exec("ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS rss_rate_limit_per_minute INTEGER NOT NULL DEFAULT 30")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS scope TEXT NOT NULL DEFAULT 'user'")
            exec("ALTER TABLE blocked_videos ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE blocked_videos ADD COLUMN IF NOT EXISTS scope TEXT NOT NULL DEFAULT 'user'")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_type TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_code TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS public_username TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT")
            exec("ALTER TABLE youtube_takeout_import_jobs ADD COLUMN IF NOT EXISTS preview_json TEXT")
            exec("ALTER TABLE youtube_sessions ADD COLUMN IF NOT EXISTS auth_user INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE bug_reports ALTER COLUMN github_issue_url TYPE TEXT")
            DatabaseSessionAuthMigration.apply()
            DatabaseOidcMigration.apply()
            DatabaseProfileAccountsMigration.apply()
            DatabaseYoutubeRemoteLoginMigration.apply()
            exec("CREATE UNIQUE INDEX IF NOT EXISTS users_public_username_unique ON users (public_username)")
            DatabasePrimaryKeyMigrations.apply()
            DatabaseIndexMigrations.apply()
            DatabaseSubscriptionsCanonicalMigration.apply()
            DatabaseImportedMediaRepairMigration.apply()
            DatabaseCollectionMetadataMigration.apply()
        }
    }
    suspend fun <T> query(block: () -> T): T = blocking { transaction { block() } }

    suspend fun <T> blocking(block: () -> T): T = withContext(queryDispatcher) { block() }

    fun healthCheck(): Boolean = runCatching {
        transaction { exec("SELECT 1") { it.next() } == true }
    }.getOrDefault(false)
}
