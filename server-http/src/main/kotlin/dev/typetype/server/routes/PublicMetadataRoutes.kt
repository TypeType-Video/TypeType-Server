package dev.typetype.server.routes

import dev.typetype.server.BuildInfo
import dev.typetype.server.INSTANCE_CACHE_CONTROL
import dev.typetype.server.models.HealthResponse
import dev.typetype.server.models.InstanceResponse
import dev.typetype.server.models.VersionResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.publicMetadataRoutes(instanceProvider: suspend () -> InstanceResponse) {
    get("/health") {
        call.respond(HealthResponse())
    }
    get("/instance") {
        call.response.headers.append(HttpHeaders.CacheControl, INSTANCE_CACHE_CONTROL)
        call.respond(instanceProvider())
    }
    get("/version") {
        call.response.headers.append(HttpHeaders.CacheControl, INSTANCE_CACHE_CONTROL)
        call.respond(
            VersionResponse(
                service = "server",
                version = BuildInfo.VERSION,
                revision = BuildInfo.REVISION,
                shortRevision = BuildInfo.SHORT_REVISION,
                buildTime = BuildInfo.BUILD_TIME,
            )
        )
    }
}
