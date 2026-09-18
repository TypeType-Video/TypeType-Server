package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class MaterialiousPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `imports and exports materialious invidious subscription json`() {
        val file = directory.resolve("materialious-export.json")
        Files.writeString(file, """{"subscriptions":["UC1","UC2"]}""")
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val adapter = MaterialiousPortabilityAdapter()
        val spool = PortabilitySpool.create(directory)

        assertEquals("invidious-subscriptions", requireNotNull(adapter.detect(input)).formatVersion)
        adapter.decode(input, spool)
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])

        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, setOf(PortabilityCategory.SUBSCRIPTIONS))
        assertTrue(output.toString().contains("\"subscriptions\":[\"UC1\",\"UC2\"]"))
        spool.delete()
    }

    @Test
    fun `accepts the opml format supported by materialious`() {
        val file = directory.resolve("materialious.opml")
        Files.writeString(
            file,
            """<opml version="1.1"><body><outline text="One" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1"/></body></opml>""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/xml")
        val spool = PortabilitySpool.create(directory)
        val adapter = MaterialiousPortabilityAdapter()

        assertEquals("opml", requireNotNull(adapter.detect(input)).formatVersion)
        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        spool.delete()
    }

    @Test
    fun `generic opml auto detection is not ambiguous with materialious`() {
        val file = directory.resolve("subscriptions.opml")
        Files.writeString(
            file,
            """<opml version="2.0"><body><outline xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1"/></body></opml>""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/xml")
        val registry = PortabilityRegistry(listOf(OpmlPortabilityAdapter(), MaterialiousPortabilityAdapter()))

        assertEquals(PortabilityFormat.OPML, registry.detect(input).second.format)
    }
}
