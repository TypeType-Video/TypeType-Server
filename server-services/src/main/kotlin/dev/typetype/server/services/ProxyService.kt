package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse

interface ProxyService {
    suspend fun pipe(url: String, rangeHeader: String?, domandBid: String? = null): ExtractionResult<ProxyResponse>

    suspend fun pipe(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        cdnRegion: String?,
        enablePrefetch: Boolean = true,
    ): ExtractionResult<ProxyResponse> = pipe(url, rangeHeader, domandBid)
}

interface ProviderMediaAwareProxyService {
    suspend fun pipeProviderMedia(
        url: String,
        rangeHeader: String?,
        domandBid: String? = null,
    ): ExtractionResult<ProxyResponse>

    suspend fun pipeProviderMediaWithAlternates(
        url: String,
        rangeHeader: String?,
        domandBid: String? = null,
        alternateUrls: List<String> = emptyList(),
    ): ExtractionResult<ProxyResponse> = pipeProviderMedia(url, rangeHeader, domandBid)

    suspend fun pipeProviderMediaWithAlternates(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        alternateUrls: List<String>,
        cdnRegion: String?,
        enablePrefetch: Boolean,
    ): ExtractionResult<ProxyResponse> = pipeProviderMediaWithAlternates(url, rangeHeader, domandBid, alternateUrls)
}
