package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import dev.typetype.server.sabr.YoutubeSabrFormat
import java.security.MessageDigest

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrLiveProtocolProbeTest {
    @Test
    fun `retrieves every requested live sabr codec`(): Unit = runBlocking {
        val videoId = sabrProbeVideoId()
        val tokenServiceUrl = sabrProbeTokenServiceUrl()
        NewPipeInitializer.init(tokenServiceUrl)
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            val tokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl)
            val prepared = SabrInfoFetcher(
                tokenClient,
                TypetypeTokenYoutubeSessionClient(tokenServiceUrl),
            ).fetchInfo(videoId) ?: error("Missing SABR player metadata")
            println(
                "profile=${prepared.info.profile} clientVersion=${prepared.info.clientVersion} " +
                    "visitorMatches=${prepared.info.visitorData == prepared.initialToken?.visitorData}"
            )
            val audio = prepared.info.formats.first { it.itag == sabrProbeAudioItag() && it.isAudio }
            val requestedVideoItags = sabrProbeVideoItags()
            val videos = requestedVideoItags.map { itag ->
                prepared.info.formats.first { it.itag == itag && it.isVideo }
            }
            assertEquals(requestedVideoItags, videos.map { it.itag })
            videos.forEach { video ->
                probe(store, videoId, prepared, audio, video)
            }
        } finally {
            store.release()
        }
    }

    private suspend fun probe(
        store: SabrSessionStore,
        videoId: String,
        prepared: SabrPreparedInfo,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ) {
        val label = "${video.codecFamily()}-${video.itag}"
        val holder = store.getOrCreate(
            videoId = videoId,
            userId = "live-protocol-probe-$label",
            info = prepared.info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = prepared.initialToken,
            startPump = false,
        )
        holder.markExpectedLive()
        store.ensureWarmed(holder, maxPumps = 8)
        val live = requireNotNull(holder.livePlaybackSnapshot())
        val segments = listOfNotNull(
            holder.observedMediaSegment(audio),
            holder.observedMediaSegment(video),
        )
        println(
            "$label requests=${holder.session.requestNumber} segments=${segments.size} " +
                "headSeq=${live.headSequence} headMs=${live.headTimeMs} startMs=${holder.resolvePlaybackStartMs(0L)}"
        )
        println("$label trace=${holder.session.diagnosticTrace}")
        assertEquals(setOf(audio.itag, video.itag), segments.map { it.header.itag }.toSet())
        segments.forEach { segment ->
            val header = segment.header
            val format = if (header.itag == audio.itag) audio else video
            val parts = requireNotNull(SabrLiveMediaNormalizer.split(format.mimeType.orEmpty(), segment.data)) {
                "$label could not split live ${format.mimeType}"
            }
            assertTrue(header.sequenceNumber > 0, "$label returned bootstrap media as playable media")
            assertTrue(parts.initialization.isNotEmpty(), "$label returned an empty initialization")
            assertTrue(parts.media.isNotEmpty(), "$label returned empty media")
            assertTrue(holder.liveInitialization(format)?.isNotEmpty() == true, "$label did not retain initialization")
            println(
                "  itag=${header.itag} seq=${header.sequenceNumber} startMs=${header.startMs} " +
                    "durationMs=${header.durationMs} bytes=${segment.length} sha256=${fingerprint(segment.data)} " +
                    "boxes=${mp4BoxNames(segment.data)} tfdt=${mp4DecodeTimes(segment.data, header.timeRangeTimescale)}"
            )
        }
    }

    private fun YoutubeSabrFormat.codecFamily(): String {
        val mime = mimeType.orEmpty().lowercase()
        return when {
            "avc1" in mime -> "H.264"
            "vp09" in mime || "vp9" in mime -> "VP9"
            "av01" in mime -> "AV1"
            else -> error("Unsupported probe codec for itag $itag: $mime")
        }
    }

    private fun mp4BoxNames(data: ByteArray): String {
        val names = mutableListOf<String>()
        var offset = 0
        while (offset + 8 <= data.size && names.size < 12) {
            val size = ((data[offset].toLong() and 0xff) shl 24) or
                ((data[offset + 1].toLong() and 0xff) shl 16) or
                ((data[offset + 2].toLong() and 0xff) shl 8) or
                (data[offset + 3].toLong() and 0xff)
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)
            if (size < 8L || size > data.size - offset) break
            names += "$type:$size"
            offset += size.toInt()
        }
        return names.joinToString(",")
    }

    private fun fingerprint(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data)
        .take(6)
        .joinToString("") { "%02x".format(it) }

    private fun mp4DecodeTimes(data: ByteArray, timescale: Int): String {
        if (timescale <= 0) return "unavailable"
        val decodeTimes = mutableListOf<Long>()
        collectDecodeTimes(data, 0, data.size, decodeTimes)
        if (decodeTimes.isEmpty()) return "none"
        val firstMs = decodeTimes.first() * 1_000L / timescale
        val lastMs = decodeTimes.last() * 1_000L / timescale
        return "count=${decodeTimes.size},firstMs=$firstMs,lastMs=$lastMs"
    }

    private fun collectDecodeTimes(data: ByteArray, start: Int, end: Int, output: MutableList<Long>) {
        var offset = start
        while (offset + 8 <= end) {
            val size = data.readUnsignedInt(offset)
            if (size < 8L || size > end - offset) return
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)
            val payloadStart = offset + 8
            val boxEnd = offset + size.toInt()
            when (type) {
                "moof", "traf" -> collectDecodeTimes(data, payloadStart, boxEnd, output)
                "tfdt" -> data.readTfdt(payloadStart, boxEnd)?.let(output::add)
            }
            offset = boxEnd
        }
    }

    private fun ByteArray.readTfdt(offset: Int, end: Int): Long? {
        if (offset + 8 > end) return null
        return if (this[offset].toInt() == 1) {
            if (offset + 12 > end) null else readUnsignedLong(offset + 4)
        } else {
            readUnsignedInt(offset + 4)
        }
    }

    private fun ByteArray.readUnsignedInt(offset: Int): Long =
        ((this[offset].toLong() and 0xff) shl 24) or
            ((this[offset + 1].toLong() and 0xff) shl 16) or
            ((this[offset + 2].toLong() and 0xff) shl 8) or
            (this[offset + 3].toLong() and 0xff)

    private fun ByteArray.readUnsignedLong(offset: Int): Long {
        var value = 0L
        repeat(8) { index -> value = (value shl 8) or (this[offset + index].toLong() and 0xff) }
        return value
    }
}
