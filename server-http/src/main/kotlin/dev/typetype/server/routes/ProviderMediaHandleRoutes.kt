package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProxyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.providerMediaHandleRoutes(
    handleService: ProviderMediaHandleService,
    proxyService: ProxyService,
) {
    get("/media/{handle}") {
        val handle = call.parameters["handle"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing media handle"))
        val target = runCatching { handleService.resolve(handle) }.getOrElse {
            return@get call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Media handle service is unavailable", "media_handle_unavailable"),
            )
        } ?: return@get call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Media handle has expired or is unknown", "media_handle_not_found"),
        )

        val rangeHeader = call.request.headers["Range"]
        val cdnRegion = call.request.queryParameters["cdn_region"]
            ?: call.request.headers["X-Bilibili-Cdn-Region"]
        val prefetchParam = call.request.queryParameters["concurrent_prefetch"]
            ?: call.request.headers["X-Bilibili-Concurrent-Prefetch"]
        val enablePrefetch = prefetchParam?.let { it != "0" && it.lowercase() != "false" } ?: true

        val result = if (proxyService is ProviderMediaAwareProxyService) {
            proxyService.pipeProviderMediaWithAlternates(
                target.url,
                rangeHeader,
                target.domandBid,
                target.alternateUrls,
                cdnRegion = cdnRegion,
                enablePrefetch = enablePrefetch,
            )
        } else {
            proxyService.pipe(target.url, rangeHeader, target.domandBid)
        }
        call.respondProxyResult(result)
    }
}
