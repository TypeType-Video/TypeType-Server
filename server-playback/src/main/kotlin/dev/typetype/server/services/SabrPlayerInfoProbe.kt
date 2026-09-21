package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.SabrAdapter

fun interface SabrPlayerInfoProbe {
    fun fetch(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        token: SabrTokenBundle,
    ): YoutubeSabrInfo
}

object PipePipeSabrPlayerInfoProbe : SabrPlayerInfoProbe {
    private val localization = Localization("en", "US")
    private val contentCountry = ContentCountry("US")

    override fun fetch(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        token: SabrTokenBundle,
    ): YoutubeSabrInfo = TypetypeYoutubeSessionPoTokenProvider.withToken(token) {
        SabrAdapter.fetchSabrInfo(videoId, profile, localization, contentCountry)
    }
}
