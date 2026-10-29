package dev.typetype.server.services

import kotlinx.coroutines.sync.Semaphore
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.concurrent.TimeUnit

data class BilibiliMirrorSwitch(
    val response: Response,
    val source: InputStream,
)

class BilibiliSlowMirrorSwitcher(
    private val execute: (Request) -> Response,
    private val alternateUrls: List<String>,
    private val permits: Semaphore,
    private val checkActive: () -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime,
    private val streamReadTimeoutMillis: Int = 10_000,
) {
    private var attempted = false

    fun maybeSwitch(
        request: Request,
        primary: Response,
        delivered: Long,
        upstreamWaitNanos: Long,
    ): BilibiliMirrorSwitch? {
        if (attempted || alternateUrls.isEmpty()) return null
        val range = parseRange(request.header("Range"))
        val media = parseContentRange(primary.header("Content-Range"))
        val etag = primary.header("ETag")?.takeIf(::isStrongEtag)
        if (range == null || media == null || etag == null ||
            primary.code != 206 ||
            media.start != range.start ||
            (range.end != null && range.end != media.end) ||
            primary.body.contentLength() != media.end - media.start + 1 ||
            media.total <= media.end
        ) {
            attempted = true
            return null
        }
        if (delivered < MIN_DELIVERED_BYTES || upstreamWaitNanos < MIN_PRIMARY_WAIT_NANOS) {
            return null
        }
        val primaryBytesPerSecond = delivered.toDouble() * NANOS_PER_SECOND / upstreamWaitNanos
        if (primaryBytesPerSecond >= MAX_PRIMARY_BYTES_PER_SECOND) return null
        val offset = media.start + delivered
        if (offset > media.end || media.end - offset + 1 < MIN_REMAINING_BYTES) {
            attempted = true
            return null
        }

        attempted = true
        if (!permits.tryAcquire()) return null
        try {
            checkActive()
            return probe(request, primary, range, media, etag, offset, primaryBytesPerSecond)
        } finally {
            permits.release()
        }
    }

    private fun probe(
        request: Request,
        primary: Response,
        range: RequestedRange,
        media: ContentRange,
        etag: String,
        offset: Long,
        primaryBytesPerSecond: Double,
    ): BilibiliMirrorSwitch? {
        val alternateUrl = alternateUrls.first()
        val rangeEnd = range.end?.toString().orEmpty()
        val suffix = "bytes=$offset-$rangeEnd"
        val candidateRequest = request.newBuilder()
            .url(alternateUrl)
            .header("Range", suffix)
            .header("If-Range", etag)
            .build()
        var candidate: Response? = null
        val probeStarted = nanoTime()
        try {
            val opened = execute(candidateRequest)
            candidate = opened
            if (elapsed(probeStarted) > MAX_PROBE_NANOS) return null
            val expectedLength = media.end - offset + 1
            val expectedRange = ContentRange(offset, media.end, media.total)
            if (!opened.isSuccessful || opened.code != 206 ||
                opened.request.url.encodedPath != primary.request.url.encodedPath ||
                parseContentRange(opened.header("Content-Range")) != expectedRange ||
                opened.header("ETag") != etag ||
                opened.header("Content-Type") != primary.header("Content-Type") ||
                opened.header("Content-Encoding") != primary.header("Content-Encoding") ||
                opened.body.contentLength() != expectedLength
            ) return null

            val input = opened.body.byteStream()
            val sampleSize = minOf(SAMPLE_BYTES.toLong(), expectedLength).toInt()
            val sample = ByteArray(sampleSize)
            var copied = 0
            val sampleStarted = nanoTime()
            while (copied < sample.size) {
                checkActive()
                if (elapsed(probeStarted) > MAX_PROBE_NANOS) return null
                val count = input.read(sample, copied, sample.size - copied)
                if (count < 0) throw EOFException("Incomplete Bilibili mirror probe")
                if (count > 0) copied += count
            }
            if (elapsed(probeStarted) > MAX_PROBE_NANOS) return null
            val sampleNanos = elapsed(sampleStarted)
            val alternateBytesPerSecond = sample.size.toDouble() * NANOS_PER_SECOND / sampleNanos
            if (alternateBytesPerSecond < MIN_ALTERNATE_BYTES_PER_SECOND ||
                alternateBytesPerSecond < primaryBytesPerSecond * MIN_SPEEDUP
            ) return null

            opened.body.source().timeout().timeout(streamReadTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            val switchedSource = SequenceInputStream(ByteArrayInputStream(sample), input)
            val switchedResponse = opened
            candidate = null
            return BilibiliMirrorSwitch(switchedResponse, switchedSource)
        } catch (error: IOException) {
            if (Thread.currentThread().isInterrupted) throw error
            return null
        } finally {
            candidate?.close()
        }
    }

    private fun elapsed(started: Long): Long = (nanoTime() - started).coerceAtLeast(1L)

    private fun parseRange(value: String?): RequestedRange? {
        val match = RANGE_PATTERN.matchEntire(value.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val endText = match.groupValues[2]
        val end = if (endText.isEmpty()) null else endText.toLongOrNull() ?: return null
        if (end != null && end < start) return null
        return RequestedRange(start, end)
    }

    private fun parseContentRange(value: String?): ContentRange? {
        val match = CONTENT_RANGE_PATTERN.matchEntire(value.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (end < start || total <= end) return null
        return ContentRange(start, end, total)
    }

    private fun isStrongEtag(value: String): Boolean =
        value.length >= 2 && value.startsWith('"') && value.endsWith('"') && !value.startsWith("W/")

    private data class RequestedRange(val start: Long, val end: Long?)
    private data class ContentRange(val start: Long, val end: Long, val total: Long)

    private companion object {
        const val MIN_DELIVERED_BYTES = 64 * 1024L
        const val MIN_REMAINING_BYTES = 32 * 1024L
        const val SAMPLE_BYTES = 32 * 1024
        const val MIN_PRIMARY_WAIT_NANOS = 1_500_000_000L
        const val MAX_PROBE_NANOS = 4_000_000_000L
        const val MAX_PRIMARY_BYTES_PER_SECOND = 384 * 1024.0
        const val MIN_ALTERNATE_BYTES_PER_SECOND = 128 * 1024.0
        const val MIN_SPEEDUP = 2.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        val RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d*)")
        val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
