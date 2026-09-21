package dev.typetype.server.services

object ExtractionErrorSanitizer {
    private const val MEMBERS_ONLY = "This video is only available for members"
    private const val SIGN_IN_REQUIRED = "Sign in is required to verify access to this video"

    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.replace('\n', ' ').replace('\r', ' ').trim()
        if (compact.isBlank()) return null
        if (isMembersOnlyPrompt(compact)) return MEMBERS_ONLY
        if (isSignInVerificationPrompt(compact)) return SIGN_IN_REQUIRED
        return compact
    }

    private fun isMembersOnlyPrompt(message: String): Boolean {
        val lowered = message.lowercase()
        if ("members" in lowered && "only available" in lowered) return true
        if ("join this channel" in lowered && "members-only" in lowered) return true
        return false
    }

    private fun isSignInVerificationPrompt(message: String): Boolean {
        val lowered = message.lowercase()
        if ("sign in to confirm" in lowered) return true
        if ("not a bot" in lowered) return true
        if ("qinisekisa ukuthi" in lowered) return true
        if ("awuyona i-bot" in lowered) return true
        if ("ngena ngemvume" in lowered) return true
        return false
    }
}
