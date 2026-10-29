package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.bilibili.BilibiliMediaUrls
import org.schabi.newpipe.extractor.stream.VideoStream

internal fun List<VideoStream>.bilibiliMediaAlternates(): Map<String, List<String>> =
    mapNotNull { stream ->
        val urls = stream.deliveryMethodInfo as? BilibiliMediaUrls ?: return@mapNotNull null
        val selected = stream.content
        if (!stream.isUrl || selected.isNullOrBlank() || urls.alternateUrls.isEmpty()) {
            null
        } else {
            selected to urls.alternateUrls
        }
    }.toMap()
