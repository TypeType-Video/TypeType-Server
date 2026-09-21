package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

object AuthenticatedYoutubeVisitorData {
    fun fetch(
        localization: Localization = Localization("en", "US"),
        contentCountry: ContentCountry = ContentCountry("US"),
    ): String {
        val headers = HashMap<String, List<String>>()
        YoutubeParsingHelper.addYoutubeHeaders(headers)
        headers["Content-Type"] = listOf("application/json")
        YoutubeParsingHelper.addLoggedInHeaders(headers)
        return YoutubeParsingHelper.getVisitorDataFromInnertube(
            InnertubeClientRequestInfo.ofWebClient(),
            localization,
            contentCountry,
            headers,
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            null,
            false,
        )
    }
}
