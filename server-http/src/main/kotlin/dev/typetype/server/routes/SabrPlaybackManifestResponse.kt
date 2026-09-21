package dev.typetype.server.routes

import dev.typetype.server.services.SabrPlaybackManifestResult
import dev.typetype.server.services.SabrPlaybackManifestService
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText

internal suspend fun ApplicationCall.respondSabrPlaybackManifest(holder: SabrSessionHolder): Unit {
    when (val result = manifestService.build(holder, SabrPlaybackPaths.mediaBasePath(holder.sessionToken))) {
        is SabrPlaybackManifestResult.Ready -> {
            response.headers.append("Cache-Control", "no-store")
            respondText(result.manifest, DASH_CONTENT_TYPE)
        }
        is SabrPlaybackManifestResult.Retry -> respond(
            HttpStatusCode.Accepted,
            holder.toRetryPlaybackResponse(result.status, RETRY_AFTER_MS),
        )
    }
}

private val manifestService = SabrPlaybackManifestService()
private const val RETRY_AFTER_MS = 1_000L
