package dev.typetype.server.services

enum class BiliBiliSessionStatus(val value: String) {
    Connected("connected"),
    NeedsReconnect("needs_reconnect"),
    Disconnected("disconnected");

    companion object {
        fun from(value: String): BiliBiliSessionStatus =
            entries.firstOrNull { it.value == value } ?: Disconnected
    }
}
