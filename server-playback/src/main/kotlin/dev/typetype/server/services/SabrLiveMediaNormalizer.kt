package dev.typetype.server.services

data class SabrLiveMediaParts(
    val initialization: ByteArray,
    val media: ByteArray,
)

object SabrLiveMediaNormalizer {
    fun split(mimeType: String, data: ByteArray): SabrLiveMediaParts? = when {
        mimeType.substringBefore(';').trim().lowercase().endsWith("/mp4") -> splitMp4(data)
        mimeType.substringBefore(';').trim().lowercase().endsWith("/webm") -> splitWebM(data)
        else -> null
    }

    private fun splitMp4(data: ByteArray): SabrLiveMediaParts? {
        var offset = 0
        var initializationEnd = -1
        while (offset + MP4_HEADER_SIZE <= data.size) {
            val size32 = data.readUnsignedInt(offset)
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)
            val headerSize = if (size32 == 1L) MP4_EXTENDED_HEADER_SIZE else MP4_HEADER_SIZE
            if (offset + headerSize > data.size) return null
            val size = when (size32) {
                0L -> data.size.toLong() - offset
                1L -> data.readUnsignedLong(offset + MP4_HEADER_SIZE) ?: return null
                else -> size32
            }
            if (size < headerSize || size > data.size.toLong() - offset) return null
            val end = offset + size.toInt()
            if (type == "moov") initializationEnd = end
            if (type == "moof" && initializationEnd > 0) {
                return SabrLiveMediaParts(
                    initialization = data.copyOfRange(0, initializationEnd),
                    media = data.copyOfRange(initializationEnd, data.size),
                )
            }
            offset = end
        }
        return null
    }

    private fun splitWebM(data: ByteArray): SabrLiveMediaParts? {
        val ebml = data.readEbmlElement(0) ?: return null
        if (ebml.id != EBML_HEADER_ID || ebml.size == null) return null
        val segmentOffset = ebml.payloadOffset + ebml.size.toInt()
        val segment = data.readEbmlElement(segmentOffset) ?: return null
        if (segment.id != WEBM_SEGMENT_ID) return null
        var offset = segment.payloadOffset
        while (offset < data.size) {
            val element = data.readEbmlElement(offset) ?: return null
            if (element.id == WEBM_CLUSTER_ID) {
                if (offset <= segment.payloadOffset) return null
                return SabrLiveMediaParts(
                    initialization = data.copyOfRange(0, offset),
                    media = data.copyOfRange(offset, data.size),
                )
            }
            val size = element.size ?: return null
            val next = element.payloadOffset.toLong() + size
            if (next <= offset || next > data.size) return null
            offset = next.toInt()
        }
        return null
    }

    private fun ByteArray.readEbmlElement(offset: Int): EbmlElement? {
        val idLength = vintLength(offset, 4) ?: return null
        val id = readRawValue(offset, idLength) ?: return null
        val sizeOffset = offset + idLength
        val sizeLength = vintLength(sizeOffset, 8) ?: return null
        val rawSize = readRawValue(sizeOffset, sizeLength) ?: return null
        val marker = 1L shl (7 * sizeLength)
        val size = (rawSize and (marker - 1L)).takeUnless { it == marker - 1L }
        return EbmlElement(id, size, sizeOffset + sizeLength)
    }

    private fun ByteArray.vintLength(offset: Int, maximum: Int): Int? {
        if (offset !in indices) return null
        val first = this[offset].toInt() and 0xff
        if (first == 0) return null
        val length = Integer.numberOfLeadingZeros(first) - 23
        return length.takeIf { it in 1..maximum && offset + it <= size }
    }

    private fun ByteArray.readRawValue(offset: Int, length: Int): Long? {
        if (length !in 1..8 || offset < 0 || offset + length > size) return null
        var value = 0L
        repeat(length) { index -> value = (value shl 8) or (this[offset + index].toLong() and 0xff) }
        return value
    }

    private fun ByteArray.readUnsignedInt(offset: Int): Long =
        ((this[offset].toLong() and 0xff) shl 24) or
            ((this[offset + 1].toLong() and 0xff) shl 16) or
            ((this[offset + 2].toLong() and 0xff) shl 8) or
            (this[offset + 3].toLong() and 0xff)

    private fun ByteArray.readUnsignedLong(offset: Int): Long? {
        val value = readRawValue(offset, 8) ?: return null
        return value.takeIf { it >= 0L }
    }

    private data class EbmlElement(val id: Long, val size: Long?, val payloadOffset: Int)

    private const val MP4_HEADER_SIZE = 8
    private const val MP4_EXTENDED_HEADER_SIZE = 16
    private const val EBML_HEADER_ID = 0x1A45DFA3L
    private const val WEBM_SEGMENT_ID = 0x18538067L
    private const val WEBM_CLUSTER_ID = 0x1F43B675L
}
