package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder

internal fun SabrSessionHolder.applyClientPreferences(): Unit {
    session.streamState.setStickyResolutionOverride(videoFormat.height.takeIf { it > 0 })
    session.streamState.setWriteLastManualSelectedResolution(true)
}
