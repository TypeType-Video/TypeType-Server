package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseImportedMediaRepairMigration {
    fun apply() {
        ensureFavoriteColumns()
        repairVideoFields(table = "history", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairVideoFields(table = "playlist_videos", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairVideoFields(table = "watch_later", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairVideoFields(table = "favorites", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "video_url")
        repairFavoritesFromMedia(table = "history")
        repairFavoritesFromMedia(table = "playlist_videos")
        repairFavoritesFromWatchLater()
        repairSubscriptionAvatars(table = "history")
        repairSubscriptionAvatars(table = "playlist_videos")
        repairChannelAvatars(table = "history")
        repairChannelAvatars(table = "playlist_videos")
        repairFavoriteAvatars()
    }

    private fun ensureFavoriteColumns() {
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS title TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS thumbnail TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS duration BIGINT NOT NULL DEFAULT 0")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS channel_name TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS channel_url TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS channel_avatar TEXT NOT NULL DEFAULT ''")
    }

    private fun repairVideoFields(table: String, titleColumn: String, thumbnailColumn: String, urlColumn: String) {
        val videoId = videoIdExpression(urlColumn)
        exec("""
            UPDATE $table
            SET $thumbnailColumn = 'https://i.ytimg.com/vi/' || $videoId || '/hqdefault.jpg'
            WHERE $thumbnailColumn = '' AND $videoId IS NOT NULL
        """.trimIndent())
        exec("""
            UPDATE $table
            SET $titleColumn = 'YouTube video ' || $videoId
            WHERE $titleColumn = '' AND $videoId IS NOT NULL
        """.trimIndent())
    }

    private fun repairChannelAvatars(table: String) {
        exec("""
            UPDATE $table media
            SET channel_avatar = subscriptions.avatar_url
            FROM subscriptions
            WHERE media.channel_avatar = ''
              AND subscriptions.avatar_url <> ''
              AND media.user_id = subscriptions.user_id
              AND media.channel_url = subscriptions.channel_url
        """.trimIndent())
    }

    private fun repairSubscriptionAvatars(table: String) {
        exec("""
            UPDATE subscriptions
            SET avatar_url = media.channel_avatar
            FROM $table media
            WHERE subscriptions.avatar_url = ''
              AND media.channel_avatar <> ''
              AND media.user_id = subscriptions.user_id
              AND media.channel_url = subscriptions.channel_url
        """.trimIndent())
    }

    private fun repairFavoritesFromMedia(table: String) {
        exec("""
            UPDATE favorites fav
            SET title = CASE WHEN fav.title = '' THEN media.title ELSE fav.title END,
                thumbnail = CASE WHEN fav.thumbnail = '' THEN media.thumbnail ELSE fav.thumbnail END,
                duration = CASE WHEN fav.duration = 0 THEN media.duration ELSE fav.duration END,
                channel_name = CASE WHEN fav.channel_name = '' THEN media.channel_name ELSE fav.channel_name END,
                channel_url = CASE WHEN fav.channel_url = '' THEN media.channel_url ELSE fav.channel_url END,
                channel_avatar = CASE WHEN fav.channel_avatar = '' THEN media.channel_avatar ELSE fav.channel_avatar END
            FROM $table media
            WHERE fav.user_id = media.user_id AND fav.video_url = media.url
        """.trimIndent())
    }

    private fun repairFavoritesFromWatchLater() {
        exec("""
            UPDATE favorites fav
            SET title = CASE WHEN fav.title = '' THEN media.title ELSE fav.title END,
                thumbnail = CASE WHEN fav.thumbnail = '' THEN media.thumbnail ELSE fav.thumbnail END,
                duration = CASE WHEN fav.duration = 0 THEN media.duration ELSE fav.duration END
            FROM watch_later media
            WHERE fav.user_id = media.user_id AND fav.video_url = media.url
        """.trimIndent())
    }

    private fun repairFavoriteAvatars() {
        exec("""
            UPDATE favorites fav
            SET channel_avatar = subscriptions.avatar_url
            FROM subscriptions
            WHERE fav.channel_avatar = ''
              AND subscriptions.avatar_url <> ''
              AND fav.user_id = subscriptions.user_id
              AND fav.channel_url = subscriptions.channel_url
        """.trimIndent())
    }

    private fun videoIdExpression(urlColumn: String): String = "substring($urlColumn from '(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})')"

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
