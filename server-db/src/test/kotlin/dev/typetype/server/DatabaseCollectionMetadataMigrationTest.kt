package dev.typetype.server

import dev.typetype.server.db.DatabaseCollectionMetadataMigration
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabaseCollectionMetadataMigrationTest {
    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `repairs collection metadata from playlist videos`() {
        transaction {
            insertPlaylistVideo()
            insertWatchLater()
            insertFavorite()

            DatabaseCollectionMetadataMigration.apply()

            val watchLater = WatchLaterTable.selectAll().single()
            val favorite = FavoritesTable.selectAll().single()
            assertEquals("Channel", watchLater[WatchLaterTable.channelName])
            assertEquals("https://www.youtube.com/channel/UC1", watchLater[WatchLaterTable.channelUrl])
            assertEquals("https://avatar.test/uc1.jpg", watchLater[WatchLaterTable.channelAvatar])
            assertEquals(123L, watchLater[WatchLaterTable.viewCount])
            assertEquals(456L, watchLater[WatchLaterTable.publishedAt])
            assertEquals(123L, favorite[FavoritesTable.viewCount])
            assertEquals(456L, favorite[FavoritesTable.publishedAt])
        }
    }

    private fun insertPlaylistVideo() {
        PlaylistsTable.insert {
            it[id] = "p1"; it[userId] = TEST_USER_ID; it[name] = "Playlist"; it[description] = ""; it[createdAt] = 1L
        }
        PlaylistVideosTable.insert {
            it[id] = "v1"; it[playlistId] = "p1"; it[userId] = TEST_USER_ID; it[url] = VIDEO_URL
            it[title] = "Video"; it[thumbnail] = "thumb"; it[duration] = 10L; it[position] = 0
            it[channelName] = "Channel"; it[channelUrl] = "https://www.youtube.com/channel/UC1"
            it[channelAvatar] = "https://avatar.test/uc1.jpg"; it[viewCount] = 123L; it[publishedAt] = 456L
        }
    }

    private fun insertWatchLater() {
        WatchLaterTable.insert {
            it[userId] = TEST_USER_ID; it[url] = VIDEO_URL; it[title] = "Video"; it[thumbnail] = "thumb"; it[duration] = 10L; it[addedAt] = 1L
        }
    }

    private fun insertFavorite() {
        FavoritesTable.insert {
            it[userId] = TEST_USER_ID; it[videoUrl] = VIDEO_URL; it[favoritedAt] = 1L
        }
    }
}
