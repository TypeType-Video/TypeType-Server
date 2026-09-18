package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class LibreTubePortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter preserves libretube account data`() {
        val file = directory.resolve("libretube.json")
        Files.writeString(
            file,
            """
            {
              "format":"Piped","version":1,
              "localSubscriptions":[{"channelId":"UC1","name":"One","avatar":"avatar"}],
              "watchHistory":[{"videoId":"v1","title":"Video","duration":60}],
              "watchPositions":[{"videoId":"v1","position":12000}],
              "searchHistory":[{"query":"query"}],
              "playlistBookmarks":[{"playlistId":"PL1","playlistName":"Saved","videos":2}],
              "localPlaylists":[{"playlist":{"id":1,"name":"Local"},"videos":[{"videoId":"v1","title":"Video"}]}]
            }
            """.trimIndent(),
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = LibreTubePortabilityAdapter()

        assertEquals(PortabilityFormat.LIBRE_TUBE, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.PROGRESS])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])

        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, spool.categories())
        assertTrue(output.toString().contains("\"localSubscriptions\""))
        assertTrue(output.toString().contains("\"watchPositions\""))
        spool.delete()
    }
}
