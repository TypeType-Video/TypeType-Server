package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BilibiliShortLinkResolver
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class ShortLinkResponse(val url: String)

fun Route.shortLinkRoutes(
    resolver: BilibiliShortLinkResolver,
    authService: AuthService,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/links/b23/{code}") {
        call.accessProfileOrRespond(authService, null, adminSettingsService) ?: return@get
        val target = call.parameters["code"]?.let { resolver.resolve(it) }
        if (target == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown short link"))
            return@get
        }
        call.respond(ShortLinkResponse(target))
    }
}
