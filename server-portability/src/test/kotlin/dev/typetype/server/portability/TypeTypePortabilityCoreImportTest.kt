package dev.typetype.server.portability

import dev.typetype.server.TEST_USER_ID
import dev.typetype.server.TestDatabase
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PlaylistVideosTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TypeTypePortabilityCoreImportTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `playlist progress counts each source record once`() = runBlocking {
        val source = PortabilityBatchRecordSource(
            PortabilityCategory.PLAYLISTS,
            listOf(
                PortabilityPlaylist("favorites", "Favorites"),
                PortabilityPlaylistVideo("favorites", 0, video()),
            ),
        )
        var processed = 0

        val imported = DatabaseFactory.query {
            TypeTypePortabilityCoreImport.write(
                TEST_USER_ID,
                PortabilityCategory.PLAYLISTS,
                source,
                PortabilityDuplicatePolicy.SKIP,
            ) { processed++ }
        }

        assertEquals(2L, imported)
        assertEquals(2, processed)
        assertEquals(1L, transaction { PlaylistVideosTable.selectAll().count() })
    }

    private fun video() = PortabilityVideo(
        url = "https://youtube.com/watch?v=playlist-test",
        title = "Playlist video",
        thumbnailUrl = "",
        durationSeconds = 60L,
        channelName = "Channel",
        channelUrl = "https://youtube.com/channel/channel",
    )
}
