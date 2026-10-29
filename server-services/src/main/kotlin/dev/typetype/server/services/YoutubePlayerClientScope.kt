package dev.typetype.server.services

import org.schabi.newpipe.extractor.NewPipe

object YoutubePlayerClientScope {
    private const val CONCURRENT_SABR_PERMITS = 64
    private val gate = SharedExclusiveGate(CONCURRENT_SABR_PERMITS)

    suspend fun <T> withClient(client: YoutubePlayerClient, block: suspend () -> T): T =
        if (client == YoutubePlayerClient.MWEB) {
            gate.shared {
                NewPipe.setYoutubePlayerClient(client.value)
                block()
            }
        } else {
            gate.exclusive {
                try {
                    NewPipe.setYoutubePlayerClient(client.value)
                    block()
                } finally {
                    NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
                }
            }
        }
}
