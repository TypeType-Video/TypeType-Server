package dev.typetype.server.routes

import dev.typetype.server.services.SabrDownloadStreamer
import dev.typetype.server.services.SabrDownloadRange
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.CancellationException

internal class SabrManifestResponder(private val store: SabrSessionStore) {
    private val downloadStreamer = SabrDownloadStreamer(store)
    private val failureRecovery = SabrDownloadFailureRecovery(store)

    suspend fun respond(
        call: ApplicationCall,
        holder: SabrSessionHolder,
        videoId: String,
        audioOnly: Boolean,
        hls: Boolean,
        download: Boolean,
        downloadRange: SabrDownloadRange? = null,
    ) {
        if (!download) {
            store.startPump(holder)
            return call.respondSabrManifest(holder, videoId, audioOnly, hls)
        }
        call.response.headers.append("Cache-Control", "no-store")
        val range = requireNotNull(downloadRange)
        try {
            call.respondOutputStream(DOWNLOAD_CONTENT_TYPE, HttpStatusCode.OK) {
                try {
                    stopOnClientDisconnect { downloadStreamer.stream(holder, this, range) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failureRecovery.recover(holder, range, error)
                }
            }
        } finally {
            store.release(holder)
        }
    }

    private companion object {
        val DOWNLOAD_CONTENT_TYPE = ContentType.parse("application/vnd.typetype.sabr-download")
    }
}
