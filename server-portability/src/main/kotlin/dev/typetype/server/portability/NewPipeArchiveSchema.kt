package dev.typetype.server.portability

import java.sql.Connection

object NewPipeArchiveSchema {
    fun create(db: Connection, target: NewPipeArchiveTarget) {
        db.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = DELETE")
            statements(target).forEach(statement::execute)
            statement.execute("PRAGMA user_version = ${target.databaseVersion}")
            statement.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            statement.execute("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42,'${target.identityHash}')")
        }
        db.commit()
    }

    private fun statements(target: NewPipeArchiveTarget): List<String> = listOf(
        "CREATE TABLE subscriptions (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, url TEXT, name TEXT, avatar_url TEXT, subscriber_count INTEGER, description TEXT, notification_mode INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX index_subscriptions_service_id_url ON subscriptions(service_id,url)",
        "CREATE TABLE search_history (creation_date INTEGER, service_id INTEGER NOT NULL, search TEXT, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        "CREATE INDEX index_search_history_search ON search_history(search)",
        streams(target),
        "CREATE UNIQUE INDEX index_streams_service_id_url ON streams(service_id,url)",
        "CREATE TABLE stream_history (stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, repeat_count INTEGER NOT NULL, PRIMARY KEY(stream_id,access_date), FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE)",
        "CREATE INDEX index_stream_history_stream_id ON stream_history(stream_id)",
        "CREATE TABLE stream_state (stream_id INTEGER NOT NULL PRIMARY KEY, progress_time INTEGER NOT NULL, FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE)",
        playlists(target),
        "CREATE TABLE playlist_stream_join (playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, join_index INTEGER NOT NULL, PRIMARY KEY(playlist_id,join_index), FOREIGN KEY(playlist_id) REFERENCES playlists(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE UNIQUE INDEX index_playlist_stream_join_playlist_id_join_index ON playlist_stream_join(playlist_id,join_index)",
        "CREATE INDEX index_playlist_stream_join_stream_id ON playlist_stream_join(stream_id)",
        "CREATE TABLE remote_playlists (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, name TEXT, url TEXT, thumbnail_url TEXT, uploader TEXT, display_index INTEGER NOT NULL, stream_count INTEGER)",
        "CREATE UNIQUE INDEX index_remote_playlists_service_id_url ON remote_playlists(service_id,url)",
        "CREATE TABLE feed (stream_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, PRIMARY KEY(stream_id,subscription_id), FOREIGN KEY(stream_id) REFERENCES streams(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX index_feed_subscription_id ON feed(subscription_id)",
        "CREATE TABLE feed_group (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)",
        "CREATE INDEX index_feed_group_sort_order ON feed_group(sort_order)",
        "CREATE TABLE feed_group_subscription_join (group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, PRIMARY KEY(group_id,subscription_id), FOREIGN KEY(group_id) REFERENCES feed_group(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
        "CREATE INDEX index_feed_group_subscription_join_subscription_id ON feed_group_subscription_join(subscription_id)",
        "CREATE TABLE feed_last_updated (subscription_id INTEGER NOT NULL PRIMARY KEY, last_updated INTEGER, FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)",
    ) + targetIndexes(target)

    private fun streams(target: NewPipeArchiveTarget): String {
        val paid = if (target.pipePipe) ", is_paid INTEGER NOT NULL" else ""
        return "CREATE TABLE streams (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, service_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, duration INTEGER NOT NULL, uploader TEXT NOT NULL, uploader_url TEXT, thumbnail_url TEXT, view_count INTEGER, textual_upload_date TEXT, upload_date INTEGER, is_upload_date_approximation INTEGER$paid)"
    }

    private fun playlists(target: NewPipeArchiveTarget): String = if (target.pipePipe) {
        "CREATE TABLE playlists (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, thumbnail_url TEXT, display_index INTEGER NOT NULL)"
    } else {
        "CREATE TABLE playlists (uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT, is_thumbnail_permanent INTEGER NOT NULL, thumbnail_stream_id INTEGER NOT NULL, display_index INTEGER NOT NULL)"
    }

    private fun targetIndexes(target: NewPipeArchiveTarget): List<String> = if (target.pipePipe) {
        listOf(
            "CREATE INDEX index_playlists_name ON playlists(name)",
            "CREATE INDEX index_remote_playlists_name ON remote_playlists(name)",
        )
    } else {
        emptyList()
    }
}
