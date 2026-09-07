package dev.typetype.server.sabr

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.TypeTypeYoutubeSabrInfoFactory
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe

/** The only server entry point for PipePipe's SABR extraction API. */
internal object SabrAdapter {
    fun fetchSabrInfo(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        localization: Localization,
        contentCountry: ContentCountry,
    ): YoutubeSabrInfo = YoutubeSabrInfo(
        YoutubeSabrProbe.fetchSabrInfo(videoId, profile.delegate, localization, contentCountry),
    )

    fun fetchSabrInfo(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        localization: Localization,
        contentCountry: ContentCountry,
        poToken: String,
        visitorData: String?,
    ): YoutubeSabrInfo = YoutubeSabrInfo(
        YoutubeSabrProbe.fetchSabrInfo(
            videoId,
            profile.delegate,
            localization,
            contentCountry,
            poToken,
            visitorData,
        ),
    )

    fun fromPlayerResponse(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        cpn: String,
        response: JsonObject,
    ): YoutubeSabrInfo = YoutubeSabrInfo(
        YoutubeSabrProbe.fromPlayerResponse(videoId, profile.delegate, cpn, response),
    )

    fun withPlaybackIdentity(
        info: YoutubeSabrInfo,
        playbackUrl: String,
        clientVersion: String,
        cpn: String,
        visitorData: String?,
    ): YoutubeSabrInfo = YoutubeSabrInfo(
        TypeTypeYoutubeSabrInfoFactory.withPlaybackIdentity(
            info.delegate,
            playbackUrl,
            clientVersion,
            cpn,
            visitorData,
        ),
    )
}
