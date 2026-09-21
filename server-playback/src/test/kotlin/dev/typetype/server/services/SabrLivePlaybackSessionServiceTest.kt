package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SabrLiveMediaNormalizerTest {
    @Test
    fun `splits fragmented mp4 after movie metadata`() {
        val ftyp = mp4Box("ftyp", byteArrayOf(1, 2))
        val moov = mp4Box("moov", byteArrayOf(3, 4, 5))
        val emsg = mp4Box("emsg", byteArrayOf(6))
        val moof = mp4Box("moof", byteArrayOf(7, 8))
        val mdat = mp4Box("mdat", byteArrayOf(9, 10))

        val parts = requireNotNull(SabrLiveMediaNormalizer.split("video/mp4; codecs=avc1", ftyp + moov + emsg + moof + mdat))

        assertArrayEquals(ftyp + moov, parts.initialization)
        assertArrayEquals(emsg + moof + mdat, parts.media)
    }

    @Test
    fun `does not parse mp4 payload after first media fragment`() {
        val ftyp = mp4Box("ftyp", byteArrayOf(1))
        val moov = mp4Box("moov", byteArrayOf(2))
        val moof = mp4Box("moof", byteArrayOf(3))
        val opaqueMedia = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)

        val parts = requireNotNull(SabrLiveMediaNormalizer.split("audio/mp4", ftyp + moov + moof + opaqueMedia))

        assertArrayEquals(ftyp + moov, parts.initialization)
        assertArrayEquals(moof + opaqueMedia, parts.media)
    }

    @Test
    fun `splits webm before first cluster`() {
        val ebml = ebmlElement(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), byteArrayOf(1))
        val info = ebmlElement(byteArrayOf(0x15, 0x49, 0xA9.toByte(), 0x66), byteArrayOf(2))
        val tracks = ebmlElement(byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B), byteArrayOf(3))
        val cluster = ebmlElement(byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75), byteArrayOf(4, 5))
        val segment = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0xFF.toByte()) + info + tracks + cluster

        val parts = requireNotNull(SabrLiveMediaNormalizer.split("video/webm; codecs=vp9", ebml + segment))

        assertArrayEquals(ebml + segment.copyOfRange(0, segment.size - cluster.size), parts.initialization)
        assertArrayEquals(cluster, parts.media)
    }

    @Test
    fun `keeps malformed and regular media untouched`() {
        assertNull(SabrLiveMediaNormalizer.split("video/mp4", mp4Box("mdat", byteArrayOf(1))))
        assertNull(SabrLiveMediaNormalizer.split("video/unknown", byteArrayOf(1, 2, 3)))
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + type.toByteArray(Charsets.US_ASCII) + payload
    }

    private fun ebmlElement(id: ByteArray, payload: ByteArray): ByteArray {
        require(payload.size < 127)
        return id + byteArrayOf((0x80 or payload.size).toByte()) + payload
    }
}
