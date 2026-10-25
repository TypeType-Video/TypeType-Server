package dev.typetype.server.services

data class TokenTvPoToken(
    val poToken: String,
    val visitorData: String,
    val livingRoomPoTokenId: String,
    val generation: String? = null,
    val expiresAtMs: Long? = null,
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtMs?.let { it <= nowMs } ?: false
}
