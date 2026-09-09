package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.NicoVideoProxyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.nicoVideoProxyRoutes(nicoVideoProxyService: NicoVideoProxyService) {
    get("/proxy/nicovideo") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val rangeHeader = call.request.headers["Range"]
        val domandBid = call.request.queryParameters["domand_bid"]
        val isManifest = url.contains(".m3u8", ignoreCase = true)

        val result = if (isManifest) {
            nicoVideoProxyService.fetchManifest(url, domandBid)
        } else {
            nicoVideoProxyService.fetchSegment(url, rangeHeader, domandBid)
        }

        call.respondProxyResult(result)
    }
}
