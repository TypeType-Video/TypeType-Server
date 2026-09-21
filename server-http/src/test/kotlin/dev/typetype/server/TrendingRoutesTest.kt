package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.services.TrendingService
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

class TrendingRoutesTest {

    private val trendingService: TrendingService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { trendingRoutes(trendingService) }
        }
        block()
    }

    @Test
    fun `GET trending without service returns 400`() = withApp {
        val response = client.get("/trending")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET trending with invalid service returns 400`() = withApp {
        val response = client.get("/trending?service=notanumber")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET trending returns 200 on Success`() = withApp {
        coEvery { trendingService.getTrending(any()) } returns
            ExtractionResult.Success(emptyList<VideoItem>())
        val response = client.get("/trending?service=0")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET trending returns 422 on Failure`() = withApp {
        coEvery { trendingService.getTrending(any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/trending?service=0")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET trending returns 400 on BadRequest`() = withApp {
        coEvery { trendingService.getTrending(any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/trending?service=0")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
