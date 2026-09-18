package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GrayjayPortabilityWriter {
    fun write(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>) {
        ZipOutputStream(output.buffered()).use { zip ->
            writeObjectEntry(zip, "exportInfo", mapOf("version" to "1"))
            if (PortabilityCategory.SUBSCRIPTIONS in categories) {
                writeStore(zip, "subscriptions") { json ->
                    source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
                        (record as? PortabilitySubscription)?.let { json.writeString(it.channelUrl) }
                    }
                }
            }
            if (PortabilityCategory.SUBSCRIPTION_GROUPS in categories) writeGroups(zip, source)
            if (PortabilityCategory.HISTORY in categories) writeHistory(zip, source)
            if (PortabilityCategory.PLAYLISTS in categories) writePlaylists(zip, source)
            if (PortabilityCategory.WATCH_LATER in categories) writeWatchLater(zip, source)
            writeObjectEntry(zip, "plugins", emptyMap<String, String>())
            writeObjectEntry(zip, "plugin_settings", emptyMap<String, String>())
        }
    }

    private fun writeHistory(zip: ZipOutputStream, source: PortabilityRecordSource) = writeStore(zip, "history") { json ->
        source.forEach(PortabilityCategory.HISTORY) { record ->
            val history = record as? PortabilityHistory ?: return@forEach
            val title = history.video.title.sanitizedReconstructionText()
            json.writeString("${history.video.url}|||${history.watchedAt}|||${history.positionSeconds}|||$title")
        }
    }

    private fun writeWatchLater(zip: ZipOutputStream, source: PortabilityRecordSource) = writeStore(zip, "watch_later") { json ->
        source.forEach(PortabilityCategory.WATCH_LATER) { record ->
            (record as? PortabilityWatchLater)?.let { json.writeString(it.video.url) }
        }
    }

    private fun writePlaylists(zip: ZipOutputStream, source: PortabilityRecordSource) = writeStore(zip, "playlists") { json ->
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            val playlist = record as? PortabilityPlaylist ?: return@forEach
            val value = buildString {
                append(playlist.name.sanitizedReconstructionText())
                append(":::")
                append(playlist.sourceId.sanitizedReconstructionText())
                source.forEachChild(PortabilityCategory.PLAYLISTS, playlist.stableKey().removePrefix("playlist:")) { child ->
                    val item = child as? PortabilityPlaylistVideo ?: return@forEachChild
                    append('\n').append(item.video.url.replace("\n", ""))
                }
            }
            json.writeString(value)
        }
    }

    private fun writeGroups(zip: ZipOutputStream, source: PortabilityRecordSource) = writeStore(zip, "subscription_groups") { json ->
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            val group = record as? PortabilitySubscriptionGroup ?: return@forEach
            val value = buildGrayjayGroup(source, group)
            require(value.toByteArray().size <= PortabilityLimits.MAX_RECORD_JSON_BYTES) { "Grayjay group is too large" }
            json.writeString(value)
        }
    }

    private fun buildGrayjayGroup(source: PortabilityRecordSource, group: PortabilitySubscriptionGroup): String {
        val bytes = java.io.ByteArrayOutputStream()
        PortabilityJsonFactory.createGenerator(bytes).use { json ->
            json.writeStartObject()
            json.writeStringField("id", UUID.nameUUIDFromBytes(group.stableKey().toByteArray()).toString())
            json.writeStringField("name", group.name)
            json.writeArrayFieldStart("urls")
            source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, group.name) { child ->
                (child as? PortabilitySubscriptionGroupMembership)?.let { json.writeString(it.channelUrl) }
            }
            json.writeEndArray()
            json.writeNumberField("priority", 99)
            json.writeEndObject()
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun writeObjectEntry(zip: ZipOutputStream, name: String, values: Map<*, *>) {
        zip.putNextEntry(ZipEntry(name))
        val json = generator(zip)
        json.writeStartObject()
        values.forEach { (key, value) -> json.writeStringField(key.toString(), value.toString()) }
        json.writeEndObject()
        json.close()
        zip.closeEntry()
    }

    private inline fun writeStore(zip: ZipOutputStream, name: String, block: (JsonGenerator) -> Unit) {
        zip.putNextEntry(ZipEntry("stores/$name"))
        val json = generator(zip)
        json.writeStartArray()
        block(json)
        json.writeEndArray()
        json.close()
        zip.closeEntry()
    }

    private fun generator(output: OutputStream): JsonGenerator = PortabilityJsonFactory.createGenerator(output).apply {
        disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    }
}

private fun String.sanitizedReconstructionText(): String = replace("|||", " ").replace(":::", " ").replace("\n", " ")
