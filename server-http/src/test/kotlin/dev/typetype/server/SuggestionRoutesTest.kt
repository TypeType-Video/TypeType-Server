package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.suggestionRoutes
import dev.typetype.server.services.SuggestionService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuggestionRoutesTest {

    private val suggestionService: SuggestionService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { suggestionRoutes(suggestionService) }
        }
        block()
    }

    @Test
    fun `GET suggestions without query returns 400`() = withApp {
        val response = client.get("/suggestions?service=0")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET suggestions without service returns 400`() = withApp {
        val response = client.get("/suggestions?query=test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET suggestions with invalid service returns 400`() = withApp {
        val response = client.get("/suggestions?query=test&service=notanumber")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET suggestions returns 200 on Success`() = withApp {
        coEvery { suggestionService.getSuggestions(any(), any()) } returns
            ExtractionResult.Success(listOf("test", "testing"))
        val response = client.get("/suggestions?query=test&service=0")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET suggestions returns 422 on Failure`() = withApp {
        coEvery { suggestionService.getSuggestions(any(), any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/suggestions?query=test&service=0")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET suggestions returns 400 on BadRequest`() = withApp {
        coEvery { suggestionService.getSuggestions(any(), any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/suggestions?query=test&service=0")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
