package dev.typetype.server.services

import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe

object YoutubePlayerClientScope {
    private const val CONCURRENT_SABR_PERMITS = 64
    private val permits = Semaphore(CONCURRENT_SABR_PERMITS, true)

    suspend fun <T> withClient(client: YoutubePlayerClient, block: suspend () -> T): T {
        val permitCount = if (client == YoutubePlayerClient.MWEB) 1 else CONCURRENT_SABR_PERMITS
        withContext(Dispatchers.IO) { permits.acquire(permitCount) }
        return try {
            NewPipe.setYoutubePlayerClient(client.value)
            block()
        } finally {
            if (client != YoutubePlayerClient.MWEB) {
                NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
            }
            permits.release(permitCount)
        }
    }
}
