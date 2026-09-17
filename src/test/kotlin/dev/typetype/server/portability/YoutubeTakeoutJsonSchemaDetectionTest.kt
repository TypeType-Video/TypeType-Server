package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutJsonSchemaDetectionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `detects an activity array with an unknown localized filename`() {
        val archive = directory.resolve("takeout-json.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.putNextEntry(ZipEntry("Takeout/YouTube/donnees.json"))
            output.write(
                """[{"title":"動画を視聴しました","titleUrl":"https://www.youtube.com/watch?v=unknown01","time":"2026-09-16T18:02:08Z"}]""".toByteArray(),
            )
            output.closeEntry()
        }
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        val spool = PortabilitySpool.create(directory)

        try {
            assertEquals(PortabilityFormat.YOUTUBE_TAKEOUT, YoutubeTakeoutPortabilityAdapter().detect(input)?.format)
            YoutubeTakeoutPortabilityAdapter().decode(input, spool)
            assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        } finally {
            spool.delete()
        }
    }
}
