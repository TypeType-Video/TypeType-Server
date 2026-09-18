package dev.typetype.server.models

val DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS: Map<String, SponsorBlockMode> = mapOf(
    "sponsor" to SponsorBlockMode.AUTO_SKIP,
    "selfpromo" to SponsorBlockMode.AUTO_SKIP,
    "exclusive_access" to SponsorBlockMode.MARK_ONLY,
    "interaction" to SponsorBlockMode.AUTO_SKIP,
    "poi_highlight" to SponsorBlockMode.MARK_ONLY,
    "intro" to SponsorBlockMode.AUTO_SKIP,
    "outro" to SponsorBlockMode.AUTO_SKIP,
    "preview" to SponsorBlockMode.AUTO_SKIP,
    "filler" to SponsorBlockMode.AUTO_SKIP,
    "chapter" to SponsorBlockMode.MARK_ONLY,
    "music_offtopic" to SponsorBlockMode.AUTO_SKIP,
)

fun Map<String, SponsorBlockMode>.withDefaultSponsorBlockCategoryActions(): Map<String, SponsorBlockMode> =
    DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS + this
