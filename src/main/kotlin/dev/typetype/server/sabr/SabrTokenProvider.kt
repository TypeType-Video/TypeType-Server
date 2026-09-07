package dev.typetype.server.sabr

internal fun interface SabrPoTokenProvider {
    fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray?
}
