package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

inline fun <T> SabrSessionHolder.withPlayerContext(crossinline block: YoutubeSabrSession.() -> T): T {
    val token = playerContextToken ?: return session.block()
    return TypetypeYoutubeSessionPoTokenProvider.withToken(token) { session.block() }
}

suspend inline fun <T> SabrSessionHolder.withPlayerContextOffThread(crossinline block: YoutubeSabrSession.() -> T): T =
    runInterruptible(Dispatchers.IO) { withPlayerContext(block) }
