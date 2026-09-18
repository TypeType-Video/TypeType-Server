package dev.typetype.server.services

enum class SabrPlaybackState {
    IDLE,
    PREPARING,
    REQUESTING,
    REPOSITIONING,
    WAITING_FOR_LIVE,
    THROTTLED,
    NETWORK_FAILED,
    TERMINAL,
    STOPPED,
}
