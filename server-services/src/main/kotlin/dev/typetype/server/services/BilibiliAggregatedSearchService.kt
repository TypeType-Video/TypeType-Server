package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

class BilibiliAggregatedSearchService(
    private val delegate: SearchService,
) : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
        contentFilter: String?,
        filters: List<String>,
    ): ExtractionResult<SearchPageResponse> {
        if (serviceId != BILIBILI_SERVICE_ID || nextpage != null || contentFilter != null || filters.isNotEmpty()) {
            return delegate.search(query, serviceId, nextpage, contentFilter, filters)
        }

        return coroutineScope {
            val videoDeferred = async {
                delegate.search(
                    query = query,
                    serviceId = serviceId,
                    nextpage = null,
                    contentFilter = null,
                    filters = emptyList(),
                )
            }
            val channelDeferred = async {
                try {
                    withTimeout(BILIBILI_CHANNELS_TIMEOUT_MS) {
                        delegate.search(
                            query = query,
                            serviceId = serviceId,
                            nextpage = null,
                            contentFilter = BILIBILI_CHANNELS_FILTER,
                            filters = emptyList(),
                        )
                    }
                } catch (_: TimeoutCancellationException) {
                    null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }

            val videoResult = videoDeferred.await()
            if (videoResult !is ExtractionResult.Success) {
                return@coroutineScope videoResult
            }

            val channelResult = channelDeferred.await()
            val topChannels = if (channelResult is ExtractionResult.Success) {
                // Trust Bilibili's native relevance ranking and take the top matching creator
                channelResult.data.channels.take(1)
            } else {
                emptyList()
            }

            ExtractionResult.Success(
                videoResult.data.copy(
                    channels = topChannels,
                )
            )
        }
    }

    override suspend fun filters(
        serviceId: Int,
        contentFilter: String?,
    ): ExtractionResult<SearchFiltersResponse> = delegate.filters(serviceId, contentFilter)

    companion object {
        const val BILIBILI_CHANNELS_FILTER = "|2|channels"
        const val BILIBILI_CHANNELS_TIMEOUT_MS = 8_000L
    }
}
