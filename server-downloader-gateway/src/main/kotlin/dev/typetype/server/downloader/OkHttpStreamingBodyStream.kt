package dev.typetype.server.downloader

import java.io.FilterInputStream

internal class OkHttpStreamingBodyStream(
    private val response: okhttp3.Response,
) : FilterInputStream(response.body.byteStream()) {
    override fun close() {
        try {
            super.close()
        } finally {
            response.close()
        }
    }
}
