package dev.typetype.server

import dev.typetype.server.db.DatabaseImportedMediaRepairMigration
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabaseImportedMediaRepairMigrationTest {
    companion object {
        private const val CHANNEL_URL = "https://www.youtube.com/channel/UC1"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `repairs imported media fields already stored in database`() {
        transaction {
            insertSubscription()
            insertHistory(id = "h1", title = "", thumbnail = "", channelAvatar = "")
            insertHistory(id = "h2", title = "Known", thumbnail = "thumb", channelAvatar = "avatar")
            insertPlaylistVideo()
            insertWatchLater()
            insertFavorite()

            DatabaseImportedMediaRepairMigration.apply()

            val history = HistoryTable.selectAll().where { HistoryTable.id eq "h1" }.single()
            val subscription = SubscriptionsTable.selectAll().where { SubscriptionsTable.userId eq TEST_USER_ID }.single()
            val playlistVideo = PlaylistVideosTable.selectAll().single()
            val watchLater = WatchLaterTable.selectAll().single()
            val favorite = FavoritesTable.selectAll().single()
            assertEquals("YouTube video abc123", history[HistoryTable.title])
            assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", history[HistoryTable.thumbnail])
            assertEquals("avatar", history[HistoryTable.channelAvatar])
            assertEquals("avatar", subscription[SubscriptionsTable.avatarUrl])
            assertEquals("YouTube video pl4567", playlistVideo[PlaylistVideosTable.title])
            assertEquals("https://i.ytimg.com/vi/pl4567/hqdefault.jpg", playlistVideo[PlaylistVideosTable.thumbnail])
            assertEquals("YouTube video wl7890", watchLater[WatchLaterTable.title])
            assertEquals("https://i.ytimg.com/vi/wl7890/hqdefault.jpg", watchLater[WatchLaterTable.thumbnail])
            assertEquals("YouTube video abc123", favorite[FavoritesTable.title])
            assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", favorite[FavoritesTable.thumbnail])
        }
    }

    private fun insertSubscription() {
        SubscriptionsTable.insert {
            it[userId] = TEST_USER_ID; it[channelUrl] = CHANNEL_URL; it[name] = "Channel"; it[avatarUrl] = ""; it[subscribedAt] = 1L
        }
    }

    private fun insertHistory(id: String, title: String, thumbnail: String, channelAvatar: String) {
        HistoryTable.insert {
            it[HistoryTable.id] = id; it[userId] = TEST_USER_ID; it[url] = "https://www.youtube.com/watch?v=abc123"
            it[HistoryTable.title] = title; it[HistoryTable.thumbnail] = thumbnail; it[channelName] = "Channel"
            it[channelUrl] = CHANNEL_URL; it[HistoryTable.channelAvatar] = channelAvatar; it[duration] = 0L; it[progress] = 0L; it[watchedAt] = id.last().digitToInt().toLong()
        }
    }

    private fun insertPlaylistVideo() {
        PlaylistsTable.insert {
            it[id] = "p1"; it[userId] = TEST_USER_ID; it[name] = "Playlist"; it[description] = ""; it[createdAt] = 1L
        }
        PlaylistVideosTable.insert {
            it[id] = "v1"; it[playlistId] = "p1"; it[userId] = TEST_USER_ID; it[url] = "https://youtu.be/pl4567"
            it[title] = ""; it[thumbnail] = ""; it[duration] = 0L; it[position] = 0
        }
    }

    private fun insertWatchLater() {
        WatchLaterTable.insert {
            it[userId] = TEST_USER_ID; it[url] = "https://www.youtube.com/shorts/wl7890"; it[title] = ""; it[thumbnail] = ""; it[duration] = 0L; it[addedAt] = 1L
        }
    }

    private fun insertFavorite() {
        FavoritesTable.insert {
            it[userId] = TEST_USER_ID; it[videoUrl] = "https://www.youtube.com/watch?v=abc123"; it[favoritedAt] = 1L
        }
    }
}
