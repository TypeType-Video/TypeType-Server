package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseCollectionMetadataMigration {
    fun apply() {
        ensureColumns()
        repairWatchLaterFromPlaylists()
        repairFavoritesFromPlaylists()
        repairFavoritesFromWatchLater()
    }

    private fun ensureColumns() {
        exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS channel_name TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS channel_url TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS channel_avatar TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0")
        exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS published_at BIGINT NOT NULL DEFAULT -1")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS published_at BIGINT NOT NULL DEFAULT -1")
    }

    private fun repairWatchLaterFromPlaylists() {
        exec("""
            UPDATE watch_later target
            SET channel_name = CASE WHEN target.channel_name = '' THEN source.channel_name ELSE target.channel_name END,
                channel_url = CASE WHEN target.channel_url = '' THEN source.channel_url ELSE target.channel_url END,
                channel_avatar = CASE WHEN target.channel_avatar = '' THEN source.channel_avatar ELSE target.channel_avatar END,
                view_count = CASE WHEN target.view_count = 0 THEN source.view_count ELSE target.view_count END,
                published_at = CASE WHEN target.published_at <= 0 THEN source.published_at ELSE target.published_at END
            FROM playlist_videos source
            WHERE target.user_id = source.user_id AND target.url = source.url
        """.trimIndent())
    }

    private fun repairFavoritesFromPlaylists() {
        exec("""
            UPDATE favorites target
            SET view_count = CASE WHEN target.view_count = 0 THEN source.view_count ELSE target.view_count END,
                published_at = CASE WHEN target.published_at <= 0 THEN source.published_at ELSE target.published_at END
            FROM playlist_videos source
            WHERE target.user_id = source.user_id AND target.video_url = source.url
        """.trimIndent())
    }

    private fun repairFavoritesFromWatchLater() {
        exec("""
            UPDATE favorites target
            SET view_count = CASE WHEN target.view_count = 0 THEN source.view_count ELSE target.view_count END,
                published_at = CASE WHEN target.published_at <= 0 THEN source.published_at ELSE target.published_at END
            FROM watch_later source
            WHERE target.user_id = source.user_id AND target.video_url = source.url
        """.trimIndent())
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
