package dev.typetype.server.portability

import java.sql.Connection
import java.sql.ResultSet

object NewPipeDatabasePortabilityReader {
    fun read(connection: Connection, sink: PortabilityRecordSink) {
        subscriptions(connection, sink)
        groups(connection, sink)
        history(connection, sink)
        playlists(connection, sink)
        progress(connection, sink)
        searchHistory(connection, sink)
    }

    private fun subscriptions(db: Connection, sink: PortabilityRecordSink) = sink.readTable(
        db,
        PortabilityCategory.SUBSCRIPTIONS,
        "subscriptions",
        "SELECT service_id, url, name, avatar_url FROM subscriptions ORDER BY uid",
    ) { row ->
        if (row.int("service_id") == 0) {
            sink.write(PortabilitySubscription(row.string("url"), row.string("name"), row.string("avatar_url")))
        } else {
            sink.issue(PortabilityIssue(PortabilityCategory.SUBSCRIPTIONS, "unsupported_subscription_provider", "A non-YouTube subscription was skipped"))
        }
    }

    private fun groups(db: Connection, sink: PortabilityRecordSink) {
        sink.readTable(db, PortabilityCategory.SUBSCRIPTION_GROUPS, "feed_group", "SELECT uid, name FROM feed_group ORDER BY sort_order, uid") { row ->
            sink.write(PortabilitySubscriptionGroup(row.string("name")))
        }
        if (!db.hasTables("feed_group_subscription_join", "feed_group", "subscriptions")) return
        db.each(
            """
            SELECT g.name AS group_name, s.url AS channel_url
            FROM feed_group_subscription_join j
            JOIN feed_group g ON g.uid = j.group_id
            JOIN subscriptions s ON s.uid = j.subscription_id
            ORDER BY j.group_id, j.subscription_id
            """.trimIndent(),
        ) { row ->
            sink.write(PortabilitySubscriptionGroupMembership(row.string("group_name"), row.string("channel_url")))
        }
    }

    private fun history(db: Connection, sink: PortabilityRecordSink) = sink.readTable(
        db,
        PortabilityCategory.HISTORY,
        "stream_history",
        """
        SELECT h.access_date, s.url, s.title, s.duration, s.uploader, s.uploader_url, s.thumbnail_url
        FROM stream_history h JOIN streams s ON s.uid = h.stream_id
        ORDER BY h.access_date, h.stream_id
        """.trimIndent(),
        requiredTables = arrayOf("streams"),
    ) { row ->
        sink.write(
            PortabilityHistory(
                row.video(),
                row.long("access_date"),
            ),
        )
    }

    private fun playlists(db: Connection, sink: PortabilityRecordSink) {
        sink.readTable(db, PortabilityCategory.PLAYLISTS, "playlists", "SELECT uid, name FROM playlists ORDER BY display_index, uid") { row ->
            sink.write(PortabilityPlaylist(row.long("uid").toString(), row.string("name")))
        }
        if (db.hasTables("playlist_stream_join", "streams")) {
            db.each(
                """
                SELECT j.playlist_id, j.join_index, s.url, s.title, s.duration, s.uploader, s.uploader_url, s.thumbnail_url
                FROM playlist_stream_join j JOIN streams s ON s.uid = j.stream_id
                ORDER BY j.playlist_id, j.join_index
                """.trimIndent(),
            ) { row ->
                sink.write(PortabilityPlaylistVideo(row.long("playlist_id").toString(), row.int("join_index"), row.video()))
            }
        }
        sink.readTable(
            db,
            PortabilityCategory.SAVED_PLAYLISTS,
            "remote_playlists",
            "SELECT uid, url, name, thumbnail_url, uploader, stream_count FROM remote_playlists ORDER BY display_index, uid",
        ) { row ->
            sink.write(
                PortabilitySavedPlaylist(
                    row.long("uid").toString(), row.string("url"), row.string("name"),
                    row.string("thumbnail_url"), row.string("uploader"), row.long("stream_count"),
                ),
            )
        }
    }

    private fun progress(db: Connection, sink: PortabilityRecordSink) = sink.readTable(
        db,
        PortabilityCategory.PROGRESS,
        "stream_state",
        "SELECT s.url, state.progress_time FROM stream_state state JOIN streams s ON s.uid = state.stream_id",
        requiredTables = arrayOf("streams"),
    ) { row ->
        sink.write(PortabilityProgress(row.string("url"), row.long("progress_time")))
    }

    private fun searchHistory(db: Connection, sink: PortabilityRecordSink) = sink.readTable(
        db,
        PortabilityCategory.SEARCH_HISTORY,
        "search_history",
        "SELECT search, creation_date FROM search_history ORDER BY creation_date, id",
    ) { row ->
        sink.write(PortabilitySearchHistory(row.string("search"), row.long("creation_date")))
    }
}

private fun PortabilityRecordSink.readTable(
    db: Connection,
    category: PortabilityCategory,
    table: String,
    sql: String,
    requiredTables: Array<String> = emptyArray(),
    block: (SqliteRow) -> Unit,
) {
    if (!db.hasTables(table, *requiredTables)) return
    markCategory(category)
    db.each(sql, block)
}

private fun Connection.each(sql: String, block: (SqliteRow) -> Unit) {
    prepareStatement(sql).use { statement ->
        statement.fetchSize = 256
        statement.executeQuery().use { rows -> while (rows.next()) block(SqliteRow(rows)) }
    }
}

private fun Connection.hasTables(vararg names: String): Boolean = names.all { name ->
    prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND lower(name)=lower(?)").use { statement ->
        statement.setString(1, name)
        statement.executeQuery().use(ResultSet::next)
    }
}

private class SqliteRow(private val row: ResultSet) {
    fun string(name: String): String = row.getString(name) ?: ""
    fun int(name: String): Int = row.getInt(name)
    fun long(name: String): Long = row.getLong(name)
    fun video() = PortabilityVideo(
        string("url"), string("title"), string("thumbnail_url"), long("duration"),
        string("uploader"), string("uploader_url"),
    )
}
