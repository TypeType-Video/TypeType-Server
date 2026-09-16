package dev.typetype.server.services

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.util.Locale

object YoutubeTakeoutDateParser {
    private val ACTIVITY_PATTERNS = listOf(
        "d MMM yyyy, HH:mm:ss z",
        "d MMMM yyyy, HH:mm:ss z",
        "d. MMM yyyy, HH:mm:ss z",
        "d. MMMM yyyy, HH:mm:ss z",
        "MMM d, yyyy, HH:mm:ss z",
        "MMMM d, yyyy, HH:mm:ss z",
        "d MMM yyyy, h:mm:ss a z",
        "d MMMM yyyy, h:mm:ss a z",
        "MMM d, yyyy, h:mm:ss a z",
        "MMMM d, yyyy, h:mm:ss a z",
        "d MMM yyyy HH:mm:ss z",
        "d MMMM yyyy HH:mm:ss z",
        "MMM d, yyyy HH:mm:ss z",
        "MMMM d, yyyy HH:mm:ss z",
        "d/M/uuuu, HH:mm:ss z",
        "M/d/uuuu, HH:mm:ss z",
        "yyyy年M月d日 HH:mm:ss z",
        "yyyy年M月d日 H:mm:ss z",
        "yyyy년 M월 d일 HH:mm:ss z",
        "yyyy년 M월 d일 H:mm:ss z",
        "d M月 yyyy, HH:mm:ss z",
        "d M月 yyyy HH:mm:ss z",
        "d M월 yyyy, HH:mm:ss z",
        "d M월 yyyy HH:mm:ss z",
    )
    private val canonicalActivityFormatters = ACTIVITY_PATTERNS.map(::formatter)
    private val activityFormatters by lazy {
        localeCandidates().flatMap { locale -> ACTIVITY_PATTERNS.map { formatter(it, locale) } }
    }
    private val monthAliases by lazy(::buildMonthAliases)

    fun parseEpochMillis(value: String): Long? {
        val trimmed = value.replace("\u00a0", " ").trim()
        if (trimmed.isBlank()) return null
        parseOffset(trimmed)?.let { return it }
        parseInstant(trimmed)?.let { return it }
        parseDate(trimmed)?.let { return it }
        return parseActivityDate(trimmed)
    }

    private fun parseOffset(value: String): Long? = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parseInstant(value: String): Long? = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrNull()

    private fun parseDate(value: String): Long? = runCatching {
        LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()

    private fun parseActivityDate(value: String): Long? {
        val normalized = normalizeActivityText(value)
        val canonical = canonicalizeMonths(normalized)
        canonicalActivityFormatters.asSequence()
            .mapNotNull { parseWith(canonical, it) }
            .firstOrNull()
            ?.let { return it }
        if (!hasKnownMonth(normalized)) return null
        return activityFormatters.asSequence().mapNotNull { parseWith(normalized, it) }.firstOrNull()
    }

    private fun hasKnownMonth(value: String): Boolean = MONTH_TOKEN_REGEX.findAll(value)
        .any { monthAliases.containsKey(normalizeMonth(it.value)) }

    private fun normalizeActivityText(value: String): String = value
        .replace('\u060C', ',')
        .replace('\uFF0C', ',')
        .replace(ACTIVITY_CONNECTOR_REGEX, " ")
        .replace(ACTIVITY_SPACES_REGEX, " ")
        .trim()

    private fun parseWith(value: String, formatter: DateTimeFormatter): Long? = runCatching {
        ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
    }.getOrNull()

    private fun canonicalizeMonths(value: String): String = MONTH_TOKEN_REGEX.replace(value) { match ->
        monthAliases[normalizeMonth(match.value)] ?: match.value
    }

    private fun buildMonthAliases(): Map<String, String> {
        val aliases = mutableMapOf<String, MutableSet<Int>>()
        localeCandidates().forEach { locale ->
            Month.entries.forEach { month ->
                TextStyle.entries.filter { it != TextStyle.NARROW }.forEach { style ->
                    val alias = normalizeMonth(month.getDisplayName(style, locale))
                    if (alias.length >= 2) aliases.getOrPut(alias) { mutableSetOf() }.add(month.value)
                }
            }
        }
        return aliases.mapNotNull { (alias, months) ->
            months.singleOrNull()?.let { alias to Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        }.toMap()
    }

    private fun normalizeMonth(value: String): String =
        YoutubeTakeoutTextNormalizer.normalize(value).replace(" ", "")

    private fun localeCandidates(): List<Locale> {
        val preferred = listOf(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale.forLanguageTag("es"),
            Locale.GERMAN,
            Locale.ITALIAN,
            Locale.forLanguageTag("pt"),
            Locale.forLanguageTag("tr"),
            Locale.forLanguageTag("nl"),
            Locale.forLanguageTag("pl"),
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("uk"),
            Locale.forLanguageTag("ja"),
            Locale.KOREAN,
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
        )
        val available = Locale.getAvailableLocales().asSequence()
            .filter { it.language.isNotBlank() }
            .map { Locale.forLanguageTag(it.language) }
            .distinctBy(Locale::getLanguage)
            .toList()
        return (preferred + available).distinctBy(Locale::toLanguageTag)
    }

    private fun formatter(pattern: String): DateTimeFormatter = formatter(pattern, Locale.ENGLISH)

    private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(locale)

    private val MONTH_TOKEN_REGEX = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}.]*")
    private val ACTIVITY_CONNECTOR_REGEX = Regex("\\s+(?:de|del)\\s+", RegexOption.IGNORE_CASE)
    private val ACTIVITY_SPACES_REGEX = Regex("\\s+")
}
