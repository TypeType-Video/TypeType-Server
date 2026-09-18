package dev.typetype.server.models

data class YoutubeRemoteLoginStatus(
    val ready: Boolean,
    val unavailableReason: String?,
) {
    companion object {
        val Ready = YoutubeRemoteLoginStatus(true, null)
        val Disabled = YoutubeRemoteLoginStatus(false, "disabled")
        val NotConfigured = YoutubeRemoteLoginStatus(false, "not_configured")
        val TokenUnreachable = YoutubeRemoteLoginStatus(false, "token_unreachable")
    }
}
