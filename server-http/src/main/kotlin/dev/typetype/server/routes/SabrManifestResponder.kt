package dev.typetype.server.routes

import dev.typetype.server.services.SabrDownloadStreamer
import dev.typetype.server.services.SabrDownloadRange
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondOutputStream

internal class SabrManifestResponder(private val store: SabrSessionStore) {
    private val downloadStreamer = SabrDownloadStreamer(store)

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
        try {
            call.respondOutputStream(DOWNLOAD_CONTENT_TYPE, HttpStatusCode.OK) {
                downloadStreamer.stream(holder, this, requireNotNull(downloadRange))
            }
        } finally {
            store.release(holder)
        }
    }

    private companion object {
        val DOWNLOAD_CONTENT_TYPE = ContentType.parse("application/vnd.typetype.sabr-download")
    }
}
