package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.SabrAdapter
import org.slf4j.LoggerFactory

internal class AuthenticatedSabrInfoService(
    private val youtubeSessionService: YoutubeSessionService,
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val visitorDataFetcher: () -> String = AuthenticatedYoutubeVisitorData::fetch,
    private val probe: AuthenticatedSabrProbe = PipePipeAuthenticatedSabrProbe,
    private val cache: AuthenticatedSabrInfoCache = AuthenticatedSabrInfoCache(),
) {
    suspend fun fetch(userId: String?, videoId: String): AuthenticatedSabrInfoResult {
        if (userId == null || userId.startsWith("guest:")) return AuthenticatedSabrInfoResult.NotConnected
        val credentials = youtubeSessionService.connectedCredentials(userId)
            ?: return AuthenticatedSabrInfoResult.NotConnected
        return try {
            cache.getOrLoad(credentials, videoId) { fetchUncached(credentials, videoId) }
        } catch (error: TimeoutCancellationException) {
            logger.warn("authenticated_sabr_probe event=timeout videoId={}", videoId)
            AuthenticatedSabrInfoResult.TimedOut
        }
    }

    private suspend fun fetchUncached(
        credentials: YoutubeSessionCredentials,
        videoId: String,
    ): AuthenticatedSabrInfoResult {
        return try {
            val prepared = YoutubeSessionTokenScope.withCredentials(credentials) {
                withContext(Dispatchers.IO) {
                    val sessionBinding = runInterruptible(Dispatchers.IO) { visitorDataFetcher() }
                    val token = runInterruptible(Dispatchers.IO) { tokenClient.fetchSession(videoId, sessionBinding) }
                        ?: error("Token service did not return authenticated SABR tokens")
                    val info = runInterruptible(Dispatchers.IO) { probe.fetch(videoId, token.youtubeSessionPoToken()) }
                    SabrPreparedInfo(
                        info = info,
                        initialToken = token,
                        source = SabrPreparedSource.AUTHENTICATED_YOUTUBE,
                    ).takeIf(SabrPreparedInfo::hasAudioAndVideoFormats)
                        ?: error("Authenticated SABR response has no audio and video formats")
                }
            }
            youtubeSessionService.markUsed(credentials.userId)
            AuthenticatedSabrInfoResult.Ready(prepared)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn(
                "authenticated_sabr_probe event=failed videoId={} errorType={} error={}",
                videoId,
                error.javaClass.simpleName,
                error.message,
            )
            AuthenticatedSabrInfoResult.Failed
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(AuthenticatedSabrInfoService::class.java)
    }
}

internal sealed interface AuthenticatedSabrInfoResult {
    data object NotConnected : AuthenticatedSabrInfoResult
    data object Failed : AuthenticatedSabrInfoResult
    data object TimedOut : AuthenticatedSabrInfoResult
    data class Ready(val prepared: SabrPreparedInfo) : AuthenticatedSabrInfoResult
}

internal fun interface AuthenticatedSabrProbe {
    fun fetch(videoId: String, token: YoutubeSessionPoToken): YoutubeSabrInfo
}

private object PipePipeAuthenticatedSabrProbe : AuthenticatedSabrProbe {
    private val localization = Localization("en", "US")
    private val contentCountry = ContentCountry("US")

    override fun fetch(videoId: String, token: YoutubeSessionPoToken): YoutubeSabrInfo =
        SabrAdapter.fetchSabrInfo(
            videoId,
            YoutubeSabrClientProfile.WEB,
            localization,
            contentCountry,
            token.poToken,
            token.visitorData,
        )
}
