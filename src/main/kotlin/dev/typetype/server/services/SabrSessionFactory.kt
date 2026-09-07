package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant

internal class SabrSessionFactory(
    private val tokenClient: TypetypeTokenSabrTokenClient,
) {
    fun create(
        key: SabrSessionKey,
        info: YoutubeSabrInfo,
        audioFormat: YoutubeSabrFormat,
        videoFormat: YoutubeSabrFormat,
        sessionToken: String,
        initialToken: SabrTokenBundle?,
        initialGeneration: Long,
        source: SabrPreparedSource,
    ): SabrSessionHolder {
        val provider = TypetypeTokenSabrPoTokenProvider(tokenClient, initialToken)
        val sessionInfo = if (key.sourceId == null) info else SabrSessionIdentity.fresh(info)
        val session = YoutubeSabrSession(sessionInfo, audioFormat, videoFormat, provider)
        session.streamState.setPlayerTimeMs(key.startTimeMs)
        runCatching { provider.getPoToken(sessionInfo, session.streamState) }
            .getOrNull()
            ?.let { session.streamState.setPoToken(it) }
        return SabrSessionHolder(
            session,
            sessionInfo,
            audioFormat,
            videoFormat,
            sessionToken,
            key,
            Instant.now(),
            initialToken,
            initialGeneration = initialGeneration,
            source = source,
        ).also { it.setPlayerTimeMs(key.startTimeMs) }
    }
}
