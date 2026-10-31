package dev.typetype.server

import dev.typetype.server.services.BilibiliRangeSplitter
import dev.typetype.server.services.BilibiliSubchunkInputStream
import dev.typetype.server.services.MAX_BILIBILI_SUBCHUNK_SIZE
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class BilibiliSubchunkInputStreamTest {

    @Test
    fun `BiliBili subchunk stream handles zero byte read without advancing or closing stream`() {
        val totalRequested = 1_500_000L
        val fullData = ByteArray(totalRequested.toInt()) { (it % 256).toByte() }
        val subRanges = BilibiliRangeSplitter.splitRange(0, totalRequested - 1, MAX_BILIBILI_SUBCHUNK_SIZE)

        val request = Request.Builder().url("https://upos-sz-mirrorcosov.bilivideo.com/video.m4s").build()
        val firstChunkBytes = fullData.copyOfRange(0, subRanges[0].end.toInt() + 1)
        val firstResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .body(firstChunkBytes.toResponseBody())
            .build()

        var zeroReturned = false
        val stream = BilibiliSubchunkInputStream(
            execute = { req ->
                val rangeHeader = req.header("Range") ?: ""
                val match = Regex("""bytes=(\d+)-(\d+)""").find(rangeHeader)!!
                val s = match.groupValues[1].toInt()
                val e = match.groupValues[2].toInt()
                val slice = fullData.copyOfRange(s, e + 1)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(object : okhttp3.ResponseBody() {
                        override fun contentType() = null
                        override fun contentLength() = slice.size.toLong()
                        override fun source(): okio.BufferedSource {
                            val src = object : okio.Source {
                                var offset = 0
                                override fun timeout() = okio.Timeout.NONE
                                override fun close() {}
                                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                                    if (offset >= slice.size) return -1
                                    if (!zeroReturned) {
                                        zeroReturned = true
                                        return 0L // Yield zero bytes read!
                                    }
                                    val count = minOf(byteCount, (slice.size - offset).toLong()).toInt()
                                    sink.write(slice, offset, count)
                                    offset += count
                                    return count.toLong()
                                }
                            }
                            return src.buffer()
                        }
                    })
                    .build()
            },
            baseRequest = request,
            subRanges = subRanges,
            firstResponse = firstResponse,
            attemptsPerChunk = 3,
        )

        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val r = stream.read(buf)
            if (r == -1) break
            if (r > 0) out.write(buf, 0, r)
        }
        assertTrue(zeroReturned, "Zero-byte read should have occurred")
        assertArrayEquals(fullData, out.toByteArray())
    }
}
