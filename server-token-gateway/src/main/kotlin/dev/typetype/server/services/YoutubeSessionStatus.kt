package dev.typetype.server.services

enum class YoutubeSessionStatus(val value: String) {
    Connected("connected"),
    NeedsReconnect("needs_reconnect"),
    Disconnected("disconnected");

    companion object {
        fun from(value: String): YoutubeSessionStatus =
            entries.firstOrNull { it.value == value } ?: Disconnected
    }
}
