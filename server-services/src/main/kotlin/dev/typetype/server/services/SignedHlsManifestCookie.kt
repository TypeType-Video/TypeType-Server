package dev.typetype.server.services

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.ApplicationResponse
import java.net.URI

object SignedHlsManifestCookie {
    fun append(response: ApplicationResponse, videoUrl: String, token: String): Unit {
        cookiePaths.forEach { path ->
            response.cookies.append(
                Cookie(
                    name = name(videoUrl),
                    value = token,
                    httpOnly = true,
                    path = path,
                    maxAge = SignedHlsManifestTokenService.TTL_SECONDS.toInt(),
                    encoding = CookieEncoding.RAW,
                    extensions = mapOf("SameSite" to "Lax"),
                )
            )
        }
    }

    fun read(call: ApplicationCall, videoUrl: String): String? =
        call.request.cookies[name(videoUrl)]

    fun tokenFromPath(path: String): String? =
        runCatching {
            URI(path).rawQuery
                ?.split("&")
                ?.firstOrNull { it.startsWith("token=") }
                ?.substringAfter("token=")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    fun name(videoUrl: String): String =
        "typetype_hls_${PublicCacheKey.of("hls-cookie", videoUrl).substringAfterLast(":")}"

    private val cookiePaths = listOf("/api/streams/hls-manifest", "/streams/hls-manifest")
}
