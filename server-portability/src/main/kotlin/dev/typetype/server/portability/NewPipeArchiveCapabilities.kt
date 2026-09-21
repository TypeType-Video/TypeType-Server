package dev.typetype.server.portability

fun newPipeArchiveCapabilities(): Set<PortabilityCapability> =
    archiveImportCapabilities().mapTo(linkedSetOf()) { capability ->
        PortabilityCapability(
            capability.category,
            setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
            when (capability.category) {
                PortabilityCategory.PLAYLISTS -> PortabilityFidelity.PARTIAL
                else -> PortabilityFidelity.COMPLETE
            },
        )
    }

fun newPipeArchiveExportIssues(
    source: PortabilityRecordSource,
    categories: Set<PortabilityCategory>,
    target: NewPipeArchiveTarget,
): List<PortabilityIssue> {
    val counts = linkedMapOf<PortabilityCategory, Long>()
    categories.forEach { category ->
        source.forEach(category) { record ->
            record.urls().forEach { url ->
                if (!NewPipeProvider.supported(url, target)) counts[category] = (counts[category] ?: 0L) + 1L
            }
        }
    }
    return counts.map { (category, count) ->
        PortabilityIssue(
            category,
            "unsupported_provider",
            "${target.name.replace('_', ' ')} cannot represent one or more provider records",
            count,
        )
    }
}

private fun PortabilityRecord.urls(): List<String> = when (this) {
    is PortabilitySubscription -> listOf(channelUrl)
    is PortabilitySubscriptionGroupMembership -> listOf(channelUrl)
    is PortabilityHistory -> listOf(video.url)
    is PortabilityPlaylistVideo -> listOf(video.url)
    is PortabilityProgress -> listOf(videoUrl)
    is PortabilitySavedPlaylist -> listOf(url)
    else -> emptyList()
}
