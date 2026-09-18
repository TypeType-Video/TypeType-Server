package dev.typetype.server.services

class AvatarService {

    fun normalizeEmojiCode(raw: String): String? {
        val normalized = raw.trim().uppercase()
        if (!EMOJI_CODE_REGEX.matches(normalized)) return null
        val parts = normalized.split("-")
        if (parts.size > MAX_EMOJI_PARTS) return null
        val withoutVariationSelector = parts.filterNot { it == VARIATION_SELECTOR_16 }
        if (withoutVariationSelector.isEmpty() || withoutVariationSelector.size > MAX_EMOJI_PARTS) return null
        return withoutVariationSelector.joinToString("-")
    }

    fun openMojiPath(code: String): String = "/avatar/openmoji/$code.svg"

    fun openMojiCdnUrl(code: String): String = "$OPENMOJI_CDN_BASE/$code.svg"

    companion object {
        private const val OPENMOJI_CDN_BASE = "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg"
        private const val MAX_EMOJI_PARTS = 4
        private const val VARIATION_SELECTOR_16 = "FE0F"
        private val EMOJI_CODE_REGEX = Regex("^[0-9A-F]{2,6}(-[0-9A-F]{2,6})*$")
    }
}
