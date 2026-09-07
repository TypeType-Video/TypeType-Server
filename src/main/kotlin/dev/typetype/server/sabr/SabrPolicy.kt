package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrNextRequestPolicy as PipePolicy

internal class SabrNextRequestPolicy internal constructor(
    private val delegate: PipePolicy,
) {
    val targetAudioReadaheadMs: Int get() = delegate.targetAudioReadaheadMs
    val targetVideoReadaheadMs: Int get() = delegate.targetVideoReadaheadMs
    val maxTimeSinceLastRequestMs: Int get() = delegate.maxTimeSinceLastRequestMs
}
