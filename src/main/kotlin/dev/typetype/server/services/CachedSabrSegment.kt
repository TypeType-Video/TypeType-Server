package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import java.util.Base64

internal class CachedSabrSegment(
    val itag: Int,
    val sequence: Int,
    val init: Boolean,
    val startMs: Long,
    val durationMs: Long,
    val mimeType: String,
    val bytes: ByteArray,
) {
    constructor(
        itag: Int,
        sequence: Int,
        init: Boolean,
        startMs: Long,
        durationMs: Long,
        mimeType: String,
        bytesBase64: String,
        byteLength: Int = -1,
    ) : this(
        itag,
        sequence,
        init,
        startMs,
        durationMs,
        mimeType,
        Base64.getDecoder().decode(bytesBase64).also {
            require(byteLength < 0 || byteLength == it.size) { "byteLength does not match decoded bytes" }
        },
    )

    val length: Int get() = bytes.size
}

internal fun SabrMediaSegment.toCachedSabrSegment(
    mimeType: String,
    bytes: ByteArray = data,
): CachedSabrSegment = CachedSabrSegment(
    itag = header.itag,
    sequence = header.sequenceNumber,
    init = header.isInitSegment,
    startMs = header.startMs,
    durationMs = header.durationMs,
    mimeType = mimeType,
    bytes = bytes,
)
