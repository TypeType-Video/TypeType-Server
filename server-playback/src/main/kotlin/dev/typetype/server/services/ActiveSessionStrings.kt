package dev.typetype.server.services

object ActiveSessionStrings {
    fun text(value: String?): String? = value?.trim()?.take(MAX_TEXT_LENGTH)?.takeIf { it.isNotEmpty() }

    fun userAgent(value: String?): String? = value?.trim()?.take(MAX_USER_AGENT_LENGTH)?.takeIf { it.isNotEmpty() }

    fun key(value: String?): String = text(value) ?: "unknown"

    private const val MAX_TEXT_LENGTH = 120
    private const val MAX_USER_AGENT_LENGTH = 200
}
