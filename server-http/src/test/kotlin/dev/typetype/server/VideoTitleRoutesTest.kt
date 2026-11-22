package dev.typetype.server

import dev.typetype.server.routes.videoTitleRoutes
import dev.typetype.server.services.VideoTitleService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoTitleRoutesTest {
    private val service = VideoTitleService(fetch = { id -> if (id == "sm9") "Classic" else null })

    private fun withTitles(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { videoTitleRoutes(service) }
        }
        block()
    }

    @Test
    fun `known titles are returned by normalized id`() = withTitles {
        val response = client.get("/videos/titles?ids=SM9,av1,hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"titles":{"sm9":"Classic"}}""", response.bodyAsText())
    }

    @Test
    fun `ids are required`() = withTitles {
        assertEquals(HttpStatusCode.BadRequest, client.get("/videos/titles").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/videos/titles?ids=,").status)
    }
}
