package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class OpmlPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter imports feeds and writes portable opml`() {
        val file = directory.resolve("subscriptions.opml")
        Files.writeString(
            file,
            """<?xml version="1.0"?><opml version="2.0"><body><outline text="One" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1"/></body></opml>""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/xml")
        val spool = PortabilitySpool.create(directory)
        val adapter = OpmlPortabilityAdapter()

        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, setOf(PortabilityCategory.SUBSCRIPTIONS))
        assertTrue(output.toString().contains("channel_id=UC1"))
        spool.delete()
    }

    @Test
    fun `adapter does not resolve external entities`() {
        val file = directory.resolve("unsafe.opml")
        Files.writeString(file, """<!DOCTYPE opml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><opml><body><outline text="&xxe;"/></body></opml>""")
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/xml")
        val spool = PortabilitySpool.create(directory)

        assertThrows(Exception::class.java) { OpmlPortabilityAdapter().decode(input, spool) }
        spool.delete()
    }

    @Test
    fun `skytube and youtube local use their supported opml exchange`() {
        val file = directory.resolve("subscriptions.opml")
        Files.writeString(
            file,
            """<opml version="1.0"><body><outline text="One" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1"/></body></opml>""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/xml")
        listOf(PortabilityFormat.SKY_TUBE, PortabilityFormat.YOUTUBE_LOCAL).forEach { format ->
            val adapter = OpmlPortabilityAdapter(format, autoDetect = false)
            val spool = PortabilitySpool.create(directory)
            assertEquals(format, requireNotNull(adapter.detect(input)).format)
            adapter.decode(input, spool)
            val output = ByteArrayOutputStream()
            adapter.encode(spool, output, setOf(PortabilityCategory.SUBSCRIPTIONS))
            assertTrue(output.toString().contains("channel_id=UC1"))
            spool.delete()
        }
    }
}
