package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutActivitySignalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutActivitySignalServiceTest {
    @Test
    fun `parse extracts subscriptions and likes from activity html`() {
        val zip = createZip()
        val result = YoutubeTakeoutActivitySignalService.parse(zip)
        assertEquals(1, result.first.size)
        assertTrue(result.first.first().channelUrl.contains("youtube.com/channel/UC999"))
        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=like123",
                "https://www.youtube.com/watch?v=like456",
                "https://www.youtube.com/watch?v=like789",
            ),
            result.second.map { it.videoUrl },
        )
        assertEquals(1_783_530_662_000L, result.second.first().favoritedAt)
        assertEquals("A video", result.second.first().title)
        assertEquals("Channel Name", result.second.first().channelName)
        assertEquals("https://www.youtube.com/channel/UC999", result.second.first().channelUrl)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse omits unavailable activity favorites`() {
        val zip = createZip(
            """
            You liked <a href="https://www.youtube.com/watch?v=gone123">Deleted Video</a><br>
            8 Jul 2026, 19:11:02 CEST<br>
            """.trimIndent(),
        )

        assertTrue(YoutubeTakeoutActivitySignalService.parse(zip).second.isEmpty())
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse omits favorites with an unknown activity date`() {
        val zip = createZip(
            """
            You liked <a href="https://www.youtube.com/watch?v=unknown123">Video</a><br>
            8 Foo 2026, 19:11:02 CEST<br>
            """.trimIndent(),
        )

        assertTrue(YoutubeTakeoutActivitySignalService.parse(zip).second.isEmpty())
        Files.deleteIfExists(zip)
    }

    private fun createZip(
        html: String = """
            Vous vous êtes abonné à <a href="https://www.youtube.com/channel/UC999">Channel Name</a><br>
            Vous avez aimé <a href="https://www.youtube.com/watch?v=like123">A video</a><br>
            <a href="https://www.youtube.com/channel/UC999">Channel Name</a><br>
            8 juil. 2026, 19:11:02 CEST<br>
            A aimé <a href="https://www.youtube.com/watch?v=like456">Another video</a><br>
            5 juil. 2026, 19:10:19 CEST<br>
            Beğendiniz <a href="https://www.youtube.com/watch?v=like789">Turkish video</a><br>
            4 Jul 2026, 12:00:00 CEST<br>
        """.trimIndent(),
    ): Path {
        val zip = Files.createTempFile("yt-activity-signals-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/Mon activité/YouTube/MonActivité.html"))
            out.write("<html><body>$html</body></html>".toByteArray())
            out.closeEntry()
        }
        return zip
    }
}
