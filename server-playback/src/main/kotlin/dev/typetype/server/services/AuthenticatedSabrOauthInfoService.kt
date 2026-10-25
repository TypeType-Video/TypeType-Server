package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.sabr.SabrAdapter
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory

class AuthenticatedSabrOauthInfoService(
    private val youtubeSessionService: YoutubeSessionService,
    private val cache: AuthenticatedSabrInfoCache,
    private val tvClient: YoutubeTvOauthPlayerClient,
    private val bindingClient: TypetypeTokenYoutubeSessionClient?,
    private val sessionEpoch: ProviderSessionEpoch = ProviderSessionEpoch(),
) {
    suspend fun fetch(userId: String, videoId: String): AuthenticatedSabrInfoResult {
        val accessToken = youtubeSessionService.oauthAccessToken(userId)
            ?: return AuthenticatedSabrInfoResult.NotConnected
        val identity = PublicCacheKey.of("youtube-oauth-session", accessToken)
        return try {
            cache.getOrLoad(userId, identity, videoId, sessionEpoch.current()) {
                val player = fetchPlayer(videoId, accessToken)
                    ?: return@getOrLoad AuthenticatedSabrInfoResult.NotConnected
                when (val result = prepare(videoId, player)) {
                    is AuthenticatedSabrInfoResult.Ready -> result.copy(
                        streamResponse = player.response.toStreamResponse(videoId, result.prepared.info),
                    )
                    else -> result
                }
            }.also { result ->
                if (result is AuthenticatedSabrInfoResult.Ready) youtubeSessionService.markUsed(userId)
            }
        } catch (error: TimeoutCancellationException) {
            logger.warn("authenticated_sabr_probe event=timeout videoId={}", videoId)
            AuthenticatedSabrInfoResult.TimedOut
        }
    }

    suspend fun stream(userId: String, videoId: String): ExtractionResult<StreamResponse>? {
        if (!youtubeSessionService.isOauthSession(userId)) return null
        return when (val result = fetch(userId, videoId)) {
            is AuthenticatedSabrInfoResult.Ready -> result.streamResponse
                ?.let { ExtractionResult.Success(it) }
                ?: ExtractionResult.Failure("Authenticated SABR playback unavailable")
            AuthenticatedSabrInfoResult.NotConnected -> null
            AuthenticatedSabrInfoResult.Failed,
            AuthenticatedSabrInfoResult.TimedOut,
            -> ExtractionResult.Failure("Authenticated SABR playback unavailable")
        }
    }

    private suspend fun fetchPlayer(videoId: String, accessToken: String): OauthPlayer? {
        val tv = try {
            bindingClient?.fetchTvPoToken()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("oauth_sabr_probe event=tv_token_error videoId={}", videoId)
            null
        } ?: return null
        return try {
            tvClient.player(
                videoId,
                accessToken,
                tv.visitorData,
                tv.livingRoomPoTokenId,
                tv.poToken,
            )?.let { OauthPlayer(tv, it) }.also {
                if (it == null) logger.warn("oauth_sabr_probe event=empty_player videoId={}", videoId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn(
                "oauth_sabr_probe event=player_error videoId={} errorType={}",
                videoId,
                error::class.simpleName,
            )
            null
        }
    }

    private fun prepare(videoId: String, player: OauthPlayer): AuthenticatedSabrInfoResult {
        return try {
            val responseVisitorData = player.response.response
                .getObject("responseContext")
                ?.getString("visitorData")
                ?.takeIf(String::isNotBlank)
                ?: player.tv.visitorData
            val info = SabrAdapter.fromPlayerResponse(
                videoId,
                YoutubeSabrClientProfile.TVHTML5,
                player.response.cpn,
                player.response.response,
                responseVisitorData,
                player.response.clientVersion,
            )
            val infoWithPot = SabrAdapter.withPlaybackIdentity(
                info,
                requireNotNull(info.serverAbrStreamingUrl).withPoToken(player.tv.poToken),
                player.response.clientVersion,
                player.response.cpn,
                responseVisitorData,
            )
            val prepared = SabrPreparedInfo(
                infoWithPot,
                SabrTokenBundle.forSession(videoId, player.tv.poToken, responseVisitorData),
                isLive = player.response.response.getObject("videoDetails")?.getBoolean("isLive", false) ?: false,
                isLiveContent = player.response.response.getObject("videoDetails")?.getBoolean("isLiveContent", false) ?: false,
                source = SabrPreparedSource.AUTHENTICATED_YOUTUBE,
            )
            if (!prepared.hasAudioAndVideoFormats()) return AuthenticatedSabrInfoResult.Failed
            AuthenticatedSabrInfoResult.Ready(prepared)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("oauth_sabr_probe event=failed videoId={} errorType={}", videoId, error::class.simpleName)
            AuthenticatedSabrInfoResult.Failed
        }
    }

    private data class OauthPlayer(
        val tv: TokenTvPoToken,
        val response: TvPlayerResponse,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(AuthenticatedSabrOauthInfoService::class.java)
    }
}

private fun String.withPoToken(poToken: String): String =
    requireNotNull(toHttpUrlOrNull()) { "Invalid SABR playback URL" }
        .newBuilder()
        .setQueryParameter("pot", poToken)
        .build()
        .toString()
