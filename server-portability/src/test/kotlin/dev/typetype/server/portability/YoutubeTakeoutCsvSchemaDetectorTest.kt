package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutCsvSchemaDetectorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `classifies unknown file names from ids urls and row shape`() {
        val archive = directory.resolve("takeout-unknown-language.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.entry(
                "Takeout/YouTube/section/data-a.csv",
                "column-a,column-b,column-c\nUC123456789012,Channel,https://www.youtube.com/channel/UC123456789012\n",
            )
            output.entry(
                "Takeout/YouTube/section/data-b.csv",
                "column-a,column-b\nPL123456789,My list\n",
            )
            output.entry(
                "Takeout/YouTube/My list.csv",
                "column-a,column-b,column-c\nvideo000001,Video,2026-09-16T18:02:08Z\n",
            )
        }
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        val spool = PortabilitySpool.create(directory)

        try {
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)

            assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
            assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])
            val videos = mutableListOf<PortabilityRecord>()
            spool.forEachChild(PortabilityCategory.PLAYLISTS, "PL123456789") { videos += it }
            assertEquals(1, videos.size)
            assertTrue(spool.issues().isEmpty())
        } finally {
            spool.delete()
        }
    }

    private fun ZipOutputStream.entry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray())
        closeEntry()
    }
}
