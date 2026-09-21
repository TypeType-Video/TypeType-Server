package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

class ViewTubePortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `imports viewtube subscriptions history and progress`() {
        val archive = directory.resolve("viewtube.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("user.json"))
            zip.write(VIEW_TUBE_USER.toByteArray())
        }
        val input = PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
        val spool = PortabilitySpool.create(directory)
        val adapter = ViewTubePortabilityAdapter()

        assertEquals(PortabilityFormat.VIEW_TUBE, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.PROGRESS])
        var history: PortabilityHistory? = null
        spool.forEach(PortabilityCategory.HISTORY) { history = it as PortabilityHistory }
        assertEquals("https://www.youtube.com/watch?v=video1", history?.video?.url)
        assertEquals("A video", history?.video?.title)
        assertEquals(42L, history?.positionSeconds)
        assertTrue(spool.issues().any { it.code == "viewtube_settings_ignored" })
        spool.delete()
    }

    @Test
    fun `does not advertise a viewtube export that viewtube cannot restore`() {
        val capability = ViewTubePortabilityAdapter().descriptor.capabilities
        assertTrue(capability.isNotEmpty())
        assertTrue(capability.all { it.directions == setOf(PortabilityDirection.IMPORT) })
        assertNotNull(capability.firstOrNull { it.category == PortabilityCategory.HISTORY })
    }
}

private val VIEW_TUBE_USER = """
    {
      "username":"alice",
      "subscriptions":{"channels":[{
        "authorId":"UC1","author":"Channel","authorUrl":"https://www.youtube.com/channel/UC1",
        "authorThumbnails":[{"url":"avatar","width":88,"height":88}]
      }],"channelCount":1},
      "history":{"videos":[{
        "videoId":"video1","progressSeconds":42,"lengthSeconds":120,"lastVisit":"2026-08-20T10:15:30Z",
        "videoDetails":{"videoId":"video1","title":"A video","author":"Channel","authorId":"UC1",
          "videoThumbnails":[{"url":"thumb","width":480,"height":360}],"viewCount":12,"lengthSeconds":120}
      }],"videoCount":1},
      "settings":{"theme":"dark"}
    }
""".trimIndent()
