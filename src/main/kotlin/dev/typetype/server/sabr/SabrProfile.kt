package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile as PipeProfile

internal enum class YoutubeSabrClientProfile(internal val delegate: PipeProfile) {
    WEB(PipeProfile.WEB),
    MWEB(PipeProfile.MWEB),
    WEB_EMBEDDED(PipeProfile.WEB_EMBEDDED),
    ANDROID(PipeProfile.ANDROID),
    ANDROID_VR(PipeProfile.ANDROID_VR),
    IOS(PipeProfile.IOS),
    TVHTML5(PipeProfile.TVHTML5);

    companion object {
        internal fun fromDelegate(profile: PipeProfile): YoutubeSabrClientProfile =
            entries.first { it.delegate == profile }
    }
}
