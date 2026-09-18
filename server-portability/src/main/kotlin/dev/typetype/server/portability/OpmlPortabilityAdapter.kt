package dev.typetype.server.portability

import java.io.OutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants

class OpmlPortabilityAdapter(
    private val format: PortabilityFormat = PortabilityFormat.OPML,
    override val autoDetect: Boolean = format == PortabilityFormat.OPML,
) : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format,
        1,
        setOf(
            PortabilityCapability(
                PortabilityCategory.SUBSCRIPTIONS,
                setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
                PortabilityFidelity.PARTIAL,
            ),
        ),
        "opml",
        "application/xml",
    )

    init {
        require(format in COMPATIBLE_FORMATS) { "Format does not use OPML" }
    }

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString().trimStart()
        if (!probe.startsWith("<") || !probe.contains("<opml", ignoreCase = true)) return null
        val version = Regex("<opml[^>]*version=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
            .find(probe)?.groupValues?.get(1)
        return PortabilityDetection(format, version, 92, "OPML document root")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        val factory = secureInputFactory()
        java.nio.file.Files.newInputStream(input.path).buffered().use { stream ->
            val reader = factory.createXMLStreamReader(stream)
            var outlines = 0
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT || reader.localName != "outline") continue
                    require(outlines++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "OPML contains too many outlines" }
                    val rawUrl = reader.attribute("xmlUrl").ifBlank { reader.attribute("url") }
                    val channelUrl = normalizeOpmlChannel(rawUrl)
                    if (channelUrl.isNotBlank()) {
                        val name = reader.attribute("title").ifBlank { reader.attribute("text") }
                        sink.write(PortabilitySubscription(channelUrl, name))
                    }
                }
            } finally {
                reader.close()
            }
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        require(PortabilityCategory.SUBSCRIPTIONS in categories) { "OPML export requires subscriptions" }
        val writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, Charsets.UTF_8.name())
        writer.writeStartDocument(Charsets.UTF_8.name(), "1.0")
        writer.writeStartElement("opml")
        writer.writeAttribute("version", "2.0")
        writer.writeStartElement("head")
        writer.writeStartElement("title")
        writer.writeCharacters("TypeType subscriptions")
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeStartElement("body")
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            val item = record as PortabilitySubscription
            val channelId = youtubeId(item.channelUrl)
            writer.writeEmptyElement("outline")
            writer.writeAttribute("text", item.name.ifBlank { channelId })
            writer.writeAttribute("title", item.name.ifBlank { channelId })
            writer.writeAttribute("type", "rss")
            writer.writeAttribute("xmlUrl", "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            writer.writeAttribute("htmlUrl", item.channelUrl)
        }
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeEndDocument()
        writer.close()
    }

    private companion object {
        val COMPATIBLE_FORMATS = setOf(
            PortabilityFormat.OPML,
            PortabilityFormat.MATERIALIOUS,
            PortabilityFormat.SKY_TUBE,
            PortabilityFormat.YOUTUBE_LOCAL,
        )
    }
}

private fun secureInputFactory(): XMLInputFactory = XMLInputFactory.newFactory().apply {
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
    setProperty("javax.xml.stream.isSupportingExternalEntities", false)
}

private fun javax.xml.stream.XMLStreamReader.attribute(name: String): String =
    (0 until attributeCount).firstOrNull { getAttributeLocalName(it) == name }?.let(::getAttributeValue).orEmpty()

private fun normalizeOpmlChannel(value: String): String {
    if (value.isBlank()) return ""
    val channelId = Regex("[?&]channel_id=([^&#]+)").find(value)?.groupValues?.get(1)
    return if (channelId != null) youtubeChannelUrl(channelId) else youtubeChannelUrl(value)
}
