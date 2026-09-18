package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AudioOnlyStreamResolver
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.isYoutubeUrl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head

fun Route.audioOnlyContractRoutes(
    streamService: StreamService,
    tokenService: AudioOnlyMediaTokenService,
    authService: AuthService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
    proxyService: ProxyService? = null,
) {
    get("/streams/audio-only") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val preferOriginal = call.request.queryParameters["preferOriginal"].toBooleanParam()
        val preferredLocale = call.request.queryParameters["preferredLocale"]?.takeIf { it.isNotBlank() }
        val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
        when (val result = resolver.resolve(
            url,
            access.userId,
            preferOriginal,
            preferredLocale,
            progressivePlayable = { stream ->
                if (isYoutubeUrl(url)) proxyService?.isPlayableProgressiveAudio(stream) ?: true else true
            },
        )) {
            is ExtractionResult.Success -> {
                if (!access.profile.allowsUploader(result.data.response.uploaderUrl, result.data.response.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                when (val response = result.data.toResponse(
                    tokenService,
                    publicHlsManifestTokenService,
                    access.userId,
                    url,
                    preferOriginal,
                    preferredLocale,
                )) {
                    is ExtractionResult.Success -> call.respond(response.data)
                    is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(response.message, response.code))
                    is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(response.message, response.code))
                }
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }
}

internal fun Route.audioOnlySourceRoutes(
    streamService: StreamService,
    proxyService: ProxyService,
    tokenService: AudioOnlyMediaTokenService,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
) {
    head("/streams/audio-only/source") {
        val result = call.resolveAudioOnlySource(tokenService, streamService, youtubeSessionStreamInfo)
            ?: return@head
        call.respondAudioOnlyHead(result, proxyService)
    }
    get("/streams/audio-only/source") {
        val source = call.resolveAudioOnlySource(tokenService, streamService, youtubeSessionStreamInfo)
            ?: return@get
        when (val result = source.result) {
            is ExtractionResult.Success -> call.respondProxyResult(
                result = proxyService.pipe(
                    url = result.data.stream.url,
                    rangeHeader = proxyService.resolveAudioOnlyRangeHeader(
                        call.request.headers,
                        result.data.stream.url,
                        result.data.stream.contentLength,
                    ),
                    domandBid = null,
                )
                    .ensureProgressiveAudio(),
                contentTypeOverride = result.data.stream.mimeType,
            )
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }
}

private suspend fun ApplicationCall.resolveAudioOnlySource(
    tokenService: AudioOnlyMediaTokenService,
    streamService: StreamService,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
): AudioOnlySourceResolution? {
    val raw = request.queryParameters["token"]
        ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'token' parameter")).let { null }
    val token = when (val verified = tokenService.verify(raw)) {
        is AudioOnlyMediaTokenResult.Valid -> verified.token
        AudioOnlyMediaTokenResult.Expired -> return respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Audio-only token expired"),
        ).let { null }
        AudioOnlyMediaTokenResult.Invalid -> return respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Invalid audio-only token"),
        ).let { null }
    }
    val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
    return AudioOnlySourceResolution(
        token,
        resolver.resolve(
            token.videoUrl,
            token.userId,
            token.preferOriginal,
            token.preferredLocale,
            allowHls = false,
            allowSabr = false,
            selectedItag = token.selectedItag,
            selectedAudioTrackId = token.selectedAudioTrackId,
        ),
    )
}

private fun String?.toBooleanParam(): Boolean = equals("true", ignoreCase = true)
