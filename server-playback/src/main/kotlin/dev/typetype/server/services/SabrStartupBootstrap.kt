package dev.typetype.server.services

fun SabrSessionHolder.prepareStartupBootstrapPump(): Boolean {
    if (expectsLive() || !hasPendingSeek()) return false
    if (session.requestNumber == 0) {
        session.streamState.setPlayerTimeMs(0L)
        return true
    }
    if (session.requestNumber > STARTUP_BOOTSTRAP_REQUEST_LIMIT) return false
    if (session.streamState.getMaxSegment(audioFormat) > 0) return false
    if (session.streamState.getMaxSegment(videoFormat) > 0) return false
    session.streamState.setPlayerTimeMs(0L)
    return true
}

private const val STARTUP_BOOTSTRAP_REQUEST_LIMIT = 1
