package dev.typetype.server

import dev.typetype.server.services.audioMimeType
import dev.typetype.server.services.encodeUrl
import dev.typetype.server.services.rewriteManifestUrls
import dev.typetype.server.services.videoMimeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeManifestUtilsTest {
    @Test
    fun `rewrite manifest urls through proxy and preserve templates`() {
        val number = "${'$'}Number${'$'}"
        val bandwidth = "${'$'}Bandwidth${'$'}"
        val time = "${'$'}Time${'$'}"
        val manifest = """
            <BaseURL>https://r1---sn-abc.googlevideo.com/videoplayback?x=1&amp;y=2&amp;n=$number&amp;b=$bandwidth&amp;t=$time</BaseURL>
        """.trimIndent()
        val rewritten = rewriteManifestUrls(manifest)
        assertTrue(rewritten.contains("../proxy?url="))
        assertTrue(rewritten.contains(number))
        assertTrue(rewritten.contains(bandwidth))
        assertTrue(rewritten.contains(time))
    }

    @Test
    fun `encodeUrl encodes query and spaces`() {
        val encoded = encodeUrl("https://example.com/video?q=hello world&x=1")
        assertEquals("https%3A%2F%2Fexample.com%2Fvideo%3Fq%3Dhello+world%26x%3D1", encoded)
    }

    @Test
    fun `video and audio mime types map from codec prefixes`() {
        assertEquals("video/webm", videoMimeType("vp09.00.10.08"))
        assertEquals("video/webm", videoMimeType("av01.0.08M.08"))
        assertEquals("video/mp4", videoMimeType("avc1.640028"))
        assertEquals("audio/webm", audioMimeType("opus"))
        assertEquals("audio/webm", audioMimeType("vorbis"))
        assertEquals("audio/mp4", audioMimeType("mp4a.40.2"))
    }
}
