package org.schabi.newpipe.extractor.services.youtube.sabr;

public final class TypeTypeYoutubeSabrInfoFactory {
    private TypeTypeYoutubeSabrInfoFactory() {
    }

    public static YoutubeSabrInfo withPlaybackIdentity(
            final YoutubeSabrInfo info,
            final String serverAbrStreamingUrl,
            final String clientVersion,
            final String cpn,
            final String visitorData) {
        return new YoutubeSabrInfo(
                info.getProfile(),
                info.getVideoId(),
                cpn,
                clientVersion,
                visitorData,
                serverAbrStreamingUrl,
                info.getVideoPlaybackUstreamerConfig(),
                info.getFormats());
    }
}
