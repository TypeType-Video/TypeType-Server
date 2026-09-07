package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrSession

internal inline fun <T> SabrSessionHolder.withPlayerContext(crossinline block: YoutubeSabrSession.() -> T): T {
    val token = playerContextToken ?: return session.block()
    return TypetypeYoutubeSessionPoTokenProvider.withToken(token) { session.block() }
}
