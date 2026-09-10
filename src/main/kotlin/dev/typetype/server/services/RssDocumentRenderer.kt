package dev.typetype.server.services

import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.VideoItem
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

internal object RssDocumentRenderer {
    fun render(
        feed: RssFeedItem,
        videos: List<VideoItem>,
        publicBaseUrl: String,
        lastModified: Long,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, StandardCharsets.UTF_8.name())
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0")
        writer.writeStartElement("rss")
        writer.writeAttribute("version", "2.0")
        writer.writeNamespace("media", MEDIA_NAMESPACE)
        writer.writeStartElement("channel")
        writer.element("title", feed.name)
        writer.element("link", publicBaseUrl)
        writer.element("description", "TypeType subscription feed: ${feed.name}")
        writer.element("generator", "TypeType")
        writer.element("lastBuildDate", RFC_1123.format(Instant.ofEpochMilli(lastModified)))
        videos.forEach { writer.item(it, publicBaseUrl) }
        writer.writeEndElement()
        writer.writeEndElement()
        writer.writeEndDocument()
        writer.close()
        return output.toByteArray()
    }

    fun lastModified(feed: RssFeedItem, videos: List<VideoItem>, now: Long): Long =
        maxOf(feed.updatedAt, videos.maxOfOrNull(RssVideoMetadata::publishedAtMillis) ?: feed.updatedAt)
            .coerceAtMost(now)

    private fun XMLStreamWriter.item(video: VideoItem, publicBaseUrl: String) {
        val watchUrl = "$publicBaseUrl/watch?v=${URLEncoder.encode(video.url, StandardCharsets.UTF_8)}"
        writeStartElement("item")
        element("title", video.title)
        element("link", watchUrl)
        writeStartElement("guid")
        writeAttribute("isPermaLink", "false")
        writeCharacters("${RssVideoMetadata.serviceId(video)}:${video.id}")
        writeEndElement()
        element("author", video.uploaderName)
        video.shortDescription?.takeIf(String::isNotBlank)?.let { element("description", it) }
        video.thumbnailUrl.httpUrlOrNull()?.let { thumbnailUrl ->
            writeStartElement("media", "thumbnail", MEDIA_NAMESPACE)
            writeAttribute("url", thumbnailUrl)
            writeEndElement()
        }
        RssVideoMetadata.publishedAtMillis(video).takeIf { it > 0 }
            ?.let { element("pubDate", RFC_1123.format(Instant.ofEpochMilli(it))) }
        writeEndElement()
    }

    private fun XMLStreamWriter.element(name: String, value: String) {
        writeStartElement(name)
        writeCharacters(value)
        writeEndElement()
    }

    private fun String.httpUrlOrNull(): String? = runCatching {
        URI(this).takeIf { uri ->
            uri.isAbsolute && uri.host != null &&
                (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true))
        }?.toString()
    }.getOrNull()

    private const val MEDIA_NAMESPACE = "http://search.yahoo.com/mrss/"
    private val RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
}
