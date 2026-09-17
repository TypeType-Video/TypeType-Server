package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutParserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutZipScannerSchemaTest {
    @Test
    fun `parse recognizes csv schemas without translated paths`() {
        val archive = Files.createTempFile("yt-takeout-structure-", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(archive)).use { output ->
                output.entry(
                    "Takeout/YouTube/section/data-a.csv",
                    "column-a,column-b,column-c\nUC1234567890,Channel,https://www.youtube.com/channel/UC1234567890\n",
                )
                output.entry(
                    "Takeout/YouTube/section/data-b.csv",
                    "column-a,column-b\nPL123456,My list\n",
                )
                output.entry(
                    "Takeout/YouTube/section/My list.csv",
                    "column-a,column-b,column-c\nvideo000001,Video,2026-09-16T18:02:08Z\n",
                )
            }

            val parsed = YoutubeTakeoutParserService().parse(archive)

            assertEquals(1, parsed.subscriptions.size)
            assertEquals(1, parsed.playlists.size)
            assertEquals(1, parsed.playlistItems["My list"]?.size)
        } finally {
            Files.deleteIfExists(archive)
        }
    }

    private fun ZipOutputStream.entry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray())
        closeEntry()
    }
}
