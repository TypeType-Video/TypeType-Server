package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import dev.typetype.server.services.isManifestUrl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.manifestRoutes(
    manifestService: CachedManifestService,
    nativeManifestService: CachedNativeManifestService,
    hlsManifestService: HlsManifestService,
    youtubeSessionHlsManifestService: YoutubeSessionHlsManifestService? = null,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
) {
    get("/streams/manifest") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        when (val result = manifestService.dashManifest(url)) {
            is ExtractionResult.Success ->
                call.respondText(result.data, ContentType.parse("application/dash+xml"))
            is ExtractionResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }

    get("/streams/native-manifest") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        when (val result = nativeManifestService.nativeManifest(url)) {
            is ExtractionResult.Success ->
                call.respondText(result.data, ContentType.parse("application/dash+xml"))
            is ExtractionResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }

    get("/streams/hls-manifest") {
        val token = call.request.queryParameters["token"]
        if (token != null) {
            val publicResult = publicHlsManifestTokenService?.resolvePublicHlsManifest(token, hlsManifestService)
            if (publicResult != null) {
                call.respondHlsResult(publicResult, noStore = true)
                return@get
            }
            if (youtubeSessionHlsManifestService == null) {
                call.respondYoutubeSessionUnavailable(noStore = true)
                return@get
            }
            call.respondHlsResult(youtubeSessionHlsManifestService.hlsManifest(token), noStore = true)
            return@get
        }

        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val cookieToken = SignedHlsManifestCookie.read(call, url)
        if (cookieToken != null && youtubeSessionHlsManifestService != null) {
            call.respondHlsResult(youtubeSessionHlsManifestService.hlsManifest(cookieToken, url), noStore = true)
            return@get
        }

        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get

        val userId = authService?.let { call.optionalJwtUserId(it) }
        if (userId != null && youtubeSessionHlsManifestService != null && !isManifestUrl(url)) {
            call.respondHlsResult(youtubeSessionHlsManifestService.hlsManifestForUser(userId, url), noStore = true)
            return@get
        }

        call.respondHlsResult(hlsManifestService.hlsManifest(url))
    }
}

private suspend fun ApplicationCall.respondHlsResult(
    result: ExtractionResult<String>,
    noStore: Boolean = false,
): Unit {
    if (noStore) response.headers.append(HttpHeaders.CacheControl, "no-store")
    when (result) {
        is ExtractionResult.Success ->
            respondText(result.data, ContentType.parse("application/vnd.apple.mpegurl"))
        is ExtractionResult.BadRequest ->
            respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
        is ExtractionResult.Failure ->
            respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
    }
}

private suspend fun ApplicationCall.respondYoutubeSessionUnavailable(noStore: Boolean): Unit {
    if (noStore) response.headers.append(HttpHeaders.CacheControl, "no-store")
    respond(
        HttpStatusCode.ServiceUnavailable,
        ErrorResponse("YouTube Session is unavailable", "youtube_session_unavailable"),
    )
}
