package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseIndexMigrations {
    fun apply() {
        exec("CREATE EXTENSION IF NOT EXISTS pg_trgm")
        exec("CREATE INDEX IF NOT EXISTS idx_history_user_watched_id ON history (user_id, watched_at DESC, id DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_playlist_videos_user_playlist_position ON playlist_videos (user_id, playlist_id, position)")
        exec("CREATE INDEX IF NOT EXISTS idx_subscriptions_user_subscribed_at ON subscriptions (user_id, subscribed_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_favorites_user_favorited_at ON favorites (user_id, favorited_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_watch_later_user_added_at ON watch_later (user_id, added_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_search_history_user_searched_at ON search_history (user_id, searched_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_playlists_user_created_at ON playlists (user_id, created_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_saved_playlists_user_saved_at ON saved_playlists (user_id, saved_at DESC)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_playlists_user_url ON saved_playlists (user_id, url)")
        exec("CREATE INDEX IF NOT EXISTS idx_history_title_trgm ON history USING gin (lower(title) gin_trgm_ops)")
        exec("CREATE INDEX IF NOT EXISTS idx_history_channel_name_trgm ON history USING gin (lower(channel_name) gin_trgm_ops)")
        exec("CREATE INDEX IF NOT EXISTS idx_playlist_videos_user_metadata_repair ON playlist_videos (user_id) WHERE title LIKE 'YouTube video %' OR thumbnail LIKE 'https://i.ytimg.com/vi/%' OR duration <= 0 OR channel_name = '' OR channel_url = ''")
        exec("CREATE INDEX IF NOT EXISTS idx_watch_later_user_metadata_repair ON watch_later (user_id, added_at DESC) WHERE title LIKE 'YouTube video %' OR thumbnail LIKE 'https://i.ytimg.com/vi/%' OR duration <= 0 OR channel_name = '' OR channel_url = ''")
        exec("CREATE INDEX IF NOT EXISTS idx_favorites_user_metadata_repair ON favorites (user_id, favorited_at DESC) WHERE title LIKE 'YouTube video %' OR thumbnail LIKE 'https://i.ytimg.com/vi/%' OR duration <= 0 OR channel_name = '' OR channel_url = ''")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
