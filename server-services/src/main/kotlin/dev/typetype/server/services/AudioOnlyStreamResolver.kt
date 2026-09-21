package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class AudioOnlyStreamResolver(
    private val streamService: StreamService,
    private val youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
) {
    suspend fun resolve(
        url: String,
        userId: String?,
        preferOriginal: Boolean,
        preferredLocale: String?,
        allowHls: Boolean = true,
        allowSabr: Boolean = true,
        selectedItag: Int? = null,
        selectedAudioTrackId: String? = null,
        progressivePlayable: suspend (dev.typetype.server.models.AudioStreamItem) -> Boolean = { true },
    ): ExtractionResult<AudioOnlyStreamSelection> {
        val sessionResult = if (userId != null && youtubeSessionStreamInfo != null) {
            youtubeSessionStreamInfo.invoke(userId, url)
        } else {
            null
        }
        val publicResult = if (sessionResult.hasAudioStreams()) null else streamService.getStreamInfo(url)
        return when (val result = publicResult.resolveWith(sessionResult)) {
            is ExtractionResult.Success -> select(
                result.data.withSabrManifestUrls(),
                preferOriginal,
                preferredLocale,
                allowHls,
                allowSabr,
                selectedItag,
                selectedAudioTrackId,
                progressivePlayable,
            )
            is ExtractionResult.BadRequest -> result
            is ExtractionResult.Failure -> result
        }
    }

    private suspend fun select(
        response: StreamResponse,
        preferOriginal: Boolean,
        preferredLocale: String?,
        allowHls: Boolean,
        allowSabr: Boolean,
        selectedItag: Int?,
        selectedAudioTrackId: String?,
        progressivePlayable: suspend (dev.typetype.server.models.AudioStreamItem) -> Boolean,
    ): ExtractionResult<AudioOnlyStreamSelection> {
        val progressives = AudioOnlyStreamSelector.progressiveCandidates(response, preferOriginal, preferredLocale)
            .filter { selectedItag == null || it.matchesSelected(selectedItag, selectedAudioTrackId) }
        for (progressive in progressives) {
            if (progressivePlayable(progressive)) {
                return ExtractionResult.Success(AudioOnlyStreamSelection(response, progressive, AudioOnlyStreamKind.Progressive))
            }
        }
        val sabr = (if (allowSabr) sabrCandidate(response, preferOriginal, preferredLocale) else null)
            ?.takeIf { selectedItag == null || it.matchesSelected(selectedItag, selectedAudioTrackId) }
        if (sabr != null) return ExtractionResult.Success(
            AudioOnlyStreamSelection(response, sabr, AudioOnlyStreamKind.SabrHls)
        )
        if (selectedItag != null) return noAudioStream()
        val hls = (if (allowHls) hlsCandidate(response, preferOriginal, preferredLocale) else null)
        if (hls != null) return ExtractionResult.Success(AudioOnlyStreamSelection(response, hls, AudioOnlyStreamKind.Hls))
        return noAudioStream()
    }

    private fun hlsCandidate(response: StreamResponse, preferOriginal: Boolean, preferredLocale: String?) =
        AudioOnlyStreamSelector.hlsCandidates(response, preferOriginal, preferredLocale).firstOrNull()
            ?: response.hlsUrl.takeIf { it.isNotBlank() }?.let { hlsFallbackStream(it) }

    private fun sabrCandidate(response: StreamResponse, preferOriginal: Boolean, preferredLocale: String?) =
        AudioOnlyStreamSelector.sabrCandidates(response, preferOriginal, preferredLocale).firstOrNull()

    private fun dev.typetype.server.models.AudioStreamItem.matchesSelected(itag: Int, trackId: String?): Boolean =
        this.itag == itag && this.audioTrackId == trackId

    private fun noAudioStream(): ExtractionResult.Failure =
        ExtractionResult.Failure("No audio-only stream is available", "no_playable_streams")

    private fun ExtractionResult<StreamResponse>?.resolveWith(
        sessionResult: ExtractionResult<StreamResponse>?,
    ): ExtractionResult<StreamResponse> {
        if (this == null && sessionResult != null) return sessionResult
        if (this == null) return ExtractionResult.Failure("Stream extraction failed")
        if (sessionResult == null) return this
        if (this is ExtractionResult.Success && sessionResult is ExtractionResult.Success) {
            return ExtractionResult.Success(sessionResult.data.mergeWithPublic(data))
        }
        if (this is ExtractionResult.Success) return this
        return sessionResult
    }

    private fun ExtractionResult<StreamResponse>?.hasAudioStreams(): Boolean =
        this is ExtractionResult.Success && data.audioStreams.isNotEmpty()

    private fun StreamResponse.mergeWithPublic(public: StreamResponse): StreamResponse =
        copy(audioStreams = audioStreams.ifEmpty { public.audioStreams })
}
