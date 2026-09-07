package dev.typetype.server.services

import dev.typetype.server.sabr.SabrPoTokenProvider
import dev.typetype.server.sabr.SabrRecoverableException
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrStreamState

internal class TypetypeTokenSabrPoTokenProvider(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val initialToken: SabrTokenBundle? = null,
) : SabrPoTokenProvider {
    constructor(tokenServiceUrl: String) : this(TypetypeTokenSabrTokenClient(tokenServiceUrl))

    override fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray? {
        val token = if (initialToken?.videoId == info.videoId) initialToken else tokenClient.fetch(info.videoId)
        return token?.streamingPoTokenBytesFor(info)
            ?: token?.let { throw SabrRecoverableException(SABR_TOKEN_BINDING_FAILURE) }
    }
}
