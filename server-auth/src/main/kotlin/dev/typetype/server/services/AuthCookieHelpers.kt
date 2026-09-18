package dev.typetype.server.services

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.ApplicationResponse

object AuthCookieHelpers {
    const val REFRESH_COOKIE_NAME = "refresh_token"
    private const val REFRESH_COOKIE_PATH = "/"

    fun extractRefreshToken(call: ApplicationCall): String? = call.request.cookies[REFRESH_COOKIE_NAME]

    fun setRefreshCookie(response: ApplicationResponse, token: String, config: AuthSessionConfig) {
        response.cookies.append(
            Cookie(
                name = REFRESH_COOKIE_NAME,
                value = token,
                httpOnly = true,
                secure = !config.allowInsecureCookies,
                path = REFRESH_COOKIE_PATH,
                maxAge = config.refreshTtlSeconds.toInt(),
                encoding = CookieEncoding.RAW,
                extensions = mapOf("SameSite" to config.sameSite),
            )
        )
        response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
    }

    fun clearRefreshCookie(response: ApplicationResponse, config: AuthSessionConfig) {
        response.cookies.append(
            Cookie(
                name = REFRESH_COOKIE_NAME,
                value = "",
                httpOnly = true,
                secure = !config.allowInsecureCookies,
                path = REFRESH_COOKIE_PATH,
                maxAge = 0,
                encoding = CookieEncoding.RAW,
                extensions = mapOf("SameSite" to config.sameSite),
            )
        )
        response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
    }
    private val AuthSessionConfig.sameSite: String
        get() = if (allowInsecureCookies) "Lax" else "None"
}
