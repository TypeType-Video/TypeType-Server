package dev.typetype.server.downloader

import org.schabi.newpipe.extractor.downloader.Response

internal object OkHttpExtractorResponseMapper {
    fun toExtractorResponse(httpResponse: okhttp3.Response): Response {
        val responseBodyBytes = httpResponse.body.bytes()
        val responseBody = responseBodyBytes.toString(Charsets.UTF_8)
        return Response(
            httpResponse.code,
            httpResponse.message,
            httpResponse.headers.toMultimap(),
            responseBody,
            responseBodyBytes,
            httpResponse.request.url.toString(),
        )
    }
}
