package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.YouTubeSubtitleContentResult
import dev.typetype.server.services.YouTubeSubtitleDeliveryService
import dev.typetype.server.services.isYouTubeTimedTextUrl
import dev.typetype.server.services.subtitleSelectionFromTimedTextUrl
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.proxyRoutes(
    proxyService: ProxyService,
    youtubeSubtitleService: YouTubeSubtitleDeliveryService? = null,
) {
    get("/proxy") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        if (youtubeSubtitleService != null && isYouTubeTimedTextUrl(url)) {
            val selection = subtitleSelectionFromTimedTextUrl(url)
                ?: return@get call.respondYouTubeSubtitle(YouTubeSubtitleContentResult.InvalidRequest)
            return@get call.respondYouTubeSubtitle(youtubeSubtitleService.fetch(selection))
        }

        val rangeHeader = call.request.headers["Range"]
        val domandBid = call.request.queryParameters["domand_bid"]

        call.respondProxyResult(proxyService.pipe(url, rangeHeader, domandBid))
    }
}
