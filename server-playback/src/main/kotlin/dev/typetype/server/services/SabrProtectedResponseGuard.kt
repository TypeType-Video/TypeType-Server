package dev.typetype.server.services

class SabrProtectedResponseGuard(
    private val maximumResponses: Int = SabrPumpPolicy.MAX_PROTECTED_NO_MEDIA_RESPONSES,
) {
    private var lastResponseNumber: Int? = null
    private var lastGeneration: Long? = null
    private var lastPlayerTimeMs: Long? = null
    private var consecutiveResponses = 0

    fun verify(diagnosticTrace: String, generation: Long, playerTimeMs: Long): Unit {
        val response = diagnosticTrace.substringAfterLast("response n=", missingDelimiterValue = "")
        val responseNumber = response.substringBefore(' ').toIntOrNull() ?: return
        if (responseNumber == lastResponseNumber) return
        val playbackProgressed = lastGeneration?.let { it != generation } == true ||
            lastPlayerTimeMs?.let { generation == lastGeneration && playerTimeMs > it } == true
        lastResponseNumber = responseNumber
        lastGeneration = generation
        lastPlayerTimeMs = playerTimeMs
        if (!response.isProtectedNoMedia()) {
            consecutiveResponses = 0
            return
        }
        if (playbackProgressed) consecutiveResponses = 0
        consecutiveResponses++
        if (consecutiveResponses >= maximumResponses) {
            throw SabrProtectedNoMediaException(
                "SABR protected no-media response after $consecutiveResponses bounded attempts",
            )
        }
    }

    private fun String.isProtectedNoMedia(): Boolean {
        if (!contains("segments=count=0")) return false
        val status = PROTECTION.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return status >= PROTECTION_BOUNDARY
    }

    private companion object {
        const val PROTECTION_BOUNDARY = 2
        val PROTECTION = Regex("""protection=(\d+)/""")
    }
}

class SabrProtectedNoMediaException(message: String) : RuntimeException(message)
