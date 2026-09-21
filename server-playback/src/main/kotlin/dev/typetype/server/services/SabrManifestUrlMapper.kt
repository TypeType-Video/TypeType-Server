package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem

fun StreamResponse.withSabrManifestUrls(): StreamResponse = copy(
    videoStreams = videoStreams.map { it.withSabrManifestUrl(id) },
    videoOnlyStreams = videoOnlyStreams.map { it.withSabrManifestUrl(id) },
    audioStreams = audioStreams.map { it.withSabrManifestUrl(id) },
)

private fun VideoStreamItem.withSabrManifestUrl(videoId: String): VideoStreamItem =
    if (deliveryMethod == SABR_DELIVERY_METHOD) copy(manifestUrl = "/sabr/manifest/$videoId") else this

private fun AudioStreamItem.withSabrManifestUrl(videoId: String): AudioStreamItem =
    if (deliveryMethod == SABR_DELIVERY_METHOD) copy(manifestUrl = "/sabr/manifest/$videoId") else this

private const val SABR_DELIVERY_METHOD = "sabr"
