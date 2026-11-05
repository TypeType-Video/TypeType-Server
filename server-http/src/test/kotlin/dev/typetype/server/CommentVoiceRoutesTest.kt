package dev.typetype.server

import dev.typetype.server.routes.commentVoiceRoutes
import dev.typetype.server.services.CommentVoiceAudio
import dev.typetype.server.services.CommentVoiceAudioService
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentVoiceRoutesTest {
    private val voiceService: CommentVoiceAudioService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { commentVoiceRoutes(voiceService) }
        }
        block()
    }

    @Test
    fun `returns the voice reply audio`() = withApp {
        val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)
        coEvery { voiceService.audio("AGIzexZDwVE") } returns CommentVoiceAudio(bytes, "audio/mp4")

        val response = client.get("/comments/voice?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DAGIzexZDwVE")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio/mp4", response.headers[HttpHeaders.ContentType])
        assertArrayEquals(bytes, response.readRawBytes())
    }

    @Test
    fun `rejects anything that is not a YouTube link`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/comments/voice").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/comments/voice?url=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD").status,
        )
        coVerify(exactly = 0) { voiceService.audio(any()) }
    }

    @Test
    fun `reports media that is not a voice reply`() = withApp {
        coEvery { voiceService.audio(any()) } returns null

        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.get("/comments/voice?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DmHrHLqQj7Ts").status,
        )
    }
}
