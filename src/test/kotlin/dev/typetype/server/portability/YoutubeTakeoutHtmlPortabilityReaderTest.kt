package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutHtmlPortabilityReaderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `reports invalid html dates without creating epoch history`() {
        val archive = directory.resolve("takeout-invalid-date.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.putNextEntry(ZipEntry("Takeout/YouTube/Watch history.html"))
            output.write(
                ("You watched <a href=\"https://www.youtube.com/watch?v=invalid0001\">Video</a><br>" +
                    "16 Foo 2026, 12:00:00 CET<br>").toByteArray(),
            )
            output.closeEntry()
        }
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        val spool = PortabilitySpool.create(directory)

        try {
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)

            assertEquals(0L, spool.counts()[PortabilityCategory.HISTORY] ?: 0L)
            val issue = spool.issues().single()
            assertEquals("invalid_takeout_date", issue.code)
            assertEquals(1L, issue.count)
            val history = mutableListOf<PortabilityRecord>()
            spool.forEach(PortabilityCategory.HISTORY) { history += it }
            assertTrue(history.isEmpty())
        } finally {
            spool.delete()
        }
    }
}
