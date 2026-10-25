package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse

object PublicCacheAdmissions {
    fun channel(url: String, nextpage: String?, ttlSeconds: Long): (ChannelResponse) -> CacheAdmission = { response ->
        when {
            nextpage != null -> CacheAdmission.Valid(ttlSeconds)
            response.name.isBlank() -> CacheAdmission.Suspect()
            response.videos.isEmpty() && !url.isOptionalChannelTab() -> CacheAdmission.Suspect()
            else -> CacheAdmission.Valid(ttlSeconds)
        }
    }

    fun channelVideos(url: String, ttlSeconds: Long): (ChannelResponse) -> CacheAdmission = { response ->
        if (response.videos.isEmpty() && !url.isOptionalChannelTab()) {
            CacheAdmission.Suspect()
        } else {
            CacheAdmission.Valid(ttlSeconds)
        }
    }

    fun firstPage(nextpage: String?, empty: Boolean, ttlSeconds: Long): CacheAdmission =
        if (nextpage == null && empty) CacheAdmission.Suspect() else CacheAdmission.Valid(ttlSeconds)

    private fun String.isOptionalChannelTab(): Boolean = toChannelTab(null) != null
}
