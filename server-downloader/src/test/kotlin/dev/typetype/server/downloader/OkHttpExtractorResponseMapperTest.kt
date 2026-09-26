package dev.typetype.server.downloader

import java.nio.charset.Charset
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OkHttpExtractorResponseMapperTest {

    private fun httpResponse(contentType: String?, bytes: ByteArray): okhttp3.Response =
        okhttp3.Response.Builder()
            .request(Request.Builder().url("https://example.org/complete/search").build())
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody(contentType?.toMediaType()))
            .build()

    @Test
    fun `decodes iso-8859-1 accented characters`() {
        val expected = "ä ö ü ß ë Ë é è ê ç à ñ"
        val bytes = expected.toByteArray(Charsets.ISO_8859_1)

        val mapped = OkHttpExtractorResponseMapper.toExtractorResponse(
            httpResponse("text/javascript; charset=ISO-8859-1", bytes),
        )

        assertEquals(expected, mapped.responseBody())
        assertArrayEquals(bytes, mapped.rawResponseBody())
    }

    @Test
    fun `decodes utf-8 body when charset is declared`() {
        val expected = "ä ö ü ß 🎵"

        val mapped = OkHttpExtractorResponseMapper.toExtractorResponse(
            httpResponse("application/json; charset=utf-8", expected.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(expected, mapped.responseBody())
    }

    @Test
    fun `defaults to utf-8 without content type`() {
        val expected = "ä ö ü ß 🎵"

        val mapped = OkHttpExtractorResponseMapper.toExtractorResponse(
            httpResponse(null, expected.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(expected, mapped.responseBody())
    }

    @Test
    fun `decodes shift_jis body used for japanese locales`() {
        val expected = "あいう"
        val bytes = expected.toByteArray(Charset.forName("Shift_JIS"))

        val mapped = OkHttpExtractorResponseMapper.toExtractorResponse(
            httpResponse("text/javascript; charset=Shift_JIS", bytes),
        )

        assertEquals(expected, mapped.responseBody())
    }

    @Test
    fun `decodes windows-1251 body used for cyrillic locales`() {
        val expected = "А Б В"
        val bytes = expected.toByteArray(Charset.forName("windows-1251"))

        val mapped = OkHttpExtractorResponseMapper.toExtractorResponse(
            httpResponse("text/javascript; charset=windows-1251", bytes),
        )

        assertEquals(expected, mapped.responseBody())
    }
}
