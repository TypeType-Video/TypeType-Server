package dev.typetype.server.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

val REPRESENTATION_REGEX = Regex("""<Representation[\s\S]*?</Representation>""")
private val MANIFEST_GOOGLEVIDEO_REGEX = Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/[^"<\s]+""")

fun rewriteManifestUrls(manifest: String): String =
    manifest.replace(MANIFEST_GOOGLEVIDEO_REGEX) { match ->
        val raw = match.value.replace("&amp;", "&")
        val withPlaceholders = raw
            .replace("\$Number\$", "TMPL_NUMBER")
            .replace("\$Bandwidth\$", "TMPL_BANDWIDTH")
            .replace("\$Time\$", "TMPL_TIME")
        val encoded = "../proxy?url=" + URLEncoder.encode(withPlaceholders, StandardCharsets.UTF_8)
        encoded
            .replace("TMPL_NUMBER", "\$Number\$")
            .replace("TMPL_BANDWIDTH", "\$Bandwidth\$")
            .replace("TMPL_TIME", "\$Time\$")
    }

fun encodeUrl(url: String): String = URLEncoder.encode(url, StandardCharsets.UTF_8)

fun videoMimeType(codec: String): String = when {
    codec.startsWith("vp9") || codec.startsWith("vp09") || codec.startsWith("av01") -> "video/webm"
    else -> "video/mp4"
}

fun audioMimeType(codec: String): String = when {
    codec.startsWith("opus") || codec.startsWith("vorbis") -> "audio/webm"
    else -> "audio/mp4"
}
