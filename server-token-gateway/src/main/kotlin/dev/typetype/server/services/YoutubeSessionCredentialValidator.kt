package dev.typetype.server.services

object YoutubeSessionCredentialValidator {
    private const val MAX_COOKIES_LENGTH = 32 * 1024
    private const val MAX_PO_TOKEN_LENGTH = 16 * 1024
    private val sessionCookieNames = listOf("SID", "__Secure-1PSID", "__Secure-3PSID")
    private val authorizationCookieNames = listOf("SAPISID", "__Secure-3PAPISID")

    fun isValid(cookies: String, poToken: String): Boolean {
        if (cookies.isBlank() || poToken.isBlank()) return false
        if (cookies.length > MAX_COOKIES_LENGTH || poToken.length > MAX_PO_TOKEN_LENGTH) return false
        if (poToken.length < 8) return false
        return sessionCookieNames.any { cookies.hasCookie(it) } &&
            authorizationCookieNames.any { cookies.hasCookie(it) }
    }

    private fun String.hasCookie(name: String): Boolean =
        split(';').any { part ->
            val trimmed = part.trim()
            trimmed.startsWith("$name=") && trimmed.length > name.length + 1
        }
}
