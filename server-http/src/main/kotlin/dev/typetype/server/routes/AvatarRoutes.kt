package dev.typetype.server.routes

import dev.typetype.server.services.AvatarService
import dev.typetype.server.services.OpenMojiProxyService
import dev.typetype.server.services.CustomAvatarService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.avatarRoutes(
    avatarService: AvatarService,
    openMojiProxyService: OpenMojiProxyService,
    customAvatarService: CustomAvatarService,
) {
    get("/avatar/openmoji/{code}.svg") {
        val rawCode = call.parameters["code"]
        val code = rawCode?.let(avatarService::normalizeEmojiCode)
        if (code == null) return@get call.respondText("Invalid OpenMoji code", status = HttpStatusCode.BadRequest)
        val bytes = openMojiProxyService.getSvg(code) ?: return@get call.respondText("Avatar not found", status = HttpStatusCode.NotFound)
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondBytes(bytes, ContentType.parse("image/svg+xml"))
    }
    get("/avatar/custom/{userId}/{version}") {
        val userId = call.parameters["userId"] ?: return@get call.respondText("Avatar not found", status = HttpStatusCode.NotFound)
        val version = call.parameters["version"] ?: return@get call.respondText("Avatar not found", status = HttpStatusCode.NotFound)
        val avatar = customAvatarService.get(userId, version)
            ?: return@get call.respondText("Avatar not found", status = HttpStatusCode.NotFound)
        val etag = "\"${avatar.version}\""
        call.response.headers.append(HttpHeaders.ETag, etag)
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
            return@get call.respondText("", status = HttpStatusCode.NotModified)
        }
        call.respondBytes(avatar.content, ContentType.parse(avatar.mediaType))
    }
}
