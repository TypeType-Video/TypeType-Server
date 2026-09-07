package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import dev.typetype.server.sabr.SabrProtocolException
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo

internal class SabrPlayerContextRecovery(
    private val videoId: String,
    initialToken: SabrTokenBundle,
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val probe: SabrPlayerInfoProbe,
) {
    private var activeToken = initialToken
    private var refreshAttempted = false

    fun fetch(profile: YoutubeSabrClientProfile): SabrPlayerProbeResult {
        val initialToken = activeToken
        val initialFailure = try {
            return SabrPlayerProbeResult.Success(probe.fetch(videoId, profile, initialToken), initialToken, false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }
        if (refreshAttempted || !initialFailure.isPlayerAdmissionFailure(profile)) {
            return SabrPlayerProbeResult.Failure(initialFailure, refreshAttempted)
        }
        refreshAttempted = true
        val refreshedToken = tokenClient.fetch(videoId, forceRefresh = true)
            ?: return SabrPlayerProbeResult.Failure(initialFailure, true)
        activeToken = refreshedToken
        return try {
            SabrPlayerProbeResult.Success(probe.fetch(videoId, profile, refreshedToken), refreshedToken, true)
        } catch (error: CancellationException) {
            throw error
        } catch (refreshedFailure: Exception) {
            refreshedFailure.addSuppressed(initialFailure)
            SabrPlayerProbeResult.Failure(refreshedFailure, true)
        }
    }

    private fun Exception.isPlayerAdmissionFailure(profile: YoutubeSabrClientProfile): Boolean =
        this is AntiBotException ||
            this is SabrProtocolException && message == "Player response has no streamingData for $profile"
}

internal sealed interface SabrPlayerProbeResult {
    data class Success(
        val info: YoutubeSabrInfo,
        val token: SabrTokenBundle,
        val contextRefreshed: Boolean,
    ) : SabrPlayerProbeResult

    data class Failure(
        val error: Exception,
        val contextRefreshAttempted: Boolean,
    ) : SabrPlayerProbeResult
}
