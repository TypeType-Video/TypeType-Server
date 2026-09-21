package dev.typetype.server.sabr

fun interface SabrPoTokenProvider {
    fun getPoToken(info: YoutubeSabrInfo, streamState: YoutubeSabrStreamState): ByteArray?
}
