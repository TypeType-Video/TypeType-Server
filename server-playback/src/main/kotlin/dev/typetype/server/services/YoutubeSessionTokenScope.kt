package dev.typetype.server.services

import dev.typetype.server.downloader.YoutubeAuthUserContext
import org.schabi.newpipe.extractor.ServiceList

object YoutubeSessionTokenScope {
    private const val PUBLIC_PERMITS = 64
    private val gate = SharedExclusiveGate(
        capacity = PUBLIC_PERMITS,
        maxWaitMs = PERMIT_ACQUIRE_TIMEOUT_MS,
        timeoutMessage = "Timed out waiting for YouTube extraction permits",
    )

    suspend fun <T> withCredentials(credentials: YoutubeSessionCredentials, block: suspend () -> T): T =
        gate.exclusive {
            val youtube = ServiceList.YouTube
            try {
                YoutubeAuthUserContext.set(credentials.authUser)
                youtube.setTokens(credentials.cookies)
                youtube.setAdditionalTokens(credentials.poToken)
                block()
            } finally {
                youtube.setTokens("")
                youtube.setAdditionalTokens("")
                YoutubeAuthUserContext.set(null)
            }
        }

    suspend fun <T> withoutCredentials(block: suspend () -> T): T =
        gate.shared {
            val youtube = ServiceList.YouTube
            YoutubeAuthUserContext.set(null)
            youtube.setTokens("")
            youtube.setAdditionalTokens("")
            block()
        }

    private const val PERMIT_ACQUIRE_TIMEOUT_MS = 30_000L
}
