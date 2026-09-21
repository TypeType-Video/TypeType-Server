package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class PipedPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter preserves piped account categories`() {
        val file = directory.resolve("piped.json")
        Files.writeString(
            file,
            """
            {
              "format":"Piped","version":1,
              "subscriptions":["UC1"],
              "groups":[{"groupName":"News","channels":["UC1"]}],
              "watchHistory":[{"videoId":"video1","title":"One","watchedAt":12,"currentTime":4}],
              "playlists":[{"name":"Saved","videos":["https://youtube.com/watch?v=video1"]}]
            }
            """.trimIndent(),
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = PipedPortabilityAdapter()

        assertEquals(PortabilityFormat.PIPED, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])

        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, spool.categories())
        assertTrue(output.toString().contains("\"format\":\"Piped\""))
        assertTrue(output.toString().contains("\"groupName\":\"News\""))
        spool.delete()
    }
}
