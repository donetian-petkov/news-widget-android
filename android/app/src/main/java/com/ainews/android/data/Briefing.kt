package com.ainews.android.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Hourly and daily briefings: the stories of the last hour or day, rated for how much
 * they matter, explained, grouped, and written out as a standalone HTML page with an
 * "equalizer" of sliders. A port of the Mac widget's backend/apps/api/src/briefing.ts;
 * the page itself (assets/briefing/page.html) is generated from that renderer, so both
 * apps show the same briefing.
 */

/** [hours]: how long each fixed period is; a custom one brings its own. */
enum class BriefingPeriod(val key: String, val hours: Int?) {
    Hour("hour", 1),
    Day("day", 24),
    Week("week", 168),
    Month("month", 720),
    Custom("custom", null),
    ;

    companion object {
        fun fromKey(key: String?): BriefingPeriod = entries.firstOrNull { it.key == key } ?: Hour

        /** Stories are read back at most this far, as on the Mac (which keeps them 90 days). */
        const val MAX_CUSTOM_HOURS = 90 * 24
    }
}

enum class ImpactDimension(val key: String, val label: String) {
    Security("security", "Security & conflict"),
    Politics("politics", "Politics & governance"),
    Economy("economy", "Economy & money"),
    Health("health", "Health"),
    Environment("environment", "Environment & disasters"),
    Society("society", "Society & justice"),
    Technology("technology", "Technology"),
    Culture("culture", "Culture & entertainment"),
    Sports("sports", "Sports"),
    Other("other", "Other"),
    ;

    companion object {
        fun fromKey(key: String?): ImpactDimension = entries.firstOrNull { it.key == key } ?: Other
    }
}

/** The angles every story's impact is described from, so no story is seen from one side only. */
enum class ImpactAngle(val key: String, val label: String) {
    Economic("economic", "Economic"),
    Political("political", "Political"),
    Social("social", "Social"),
    Security("security", "Security"),
}

enum class Criterion(val key: String, val label: String, val text: String) {
    Severity("severity", "Severity", "harm to lives, safety, rights or money"),
    Scale("scale", "Scale", "how many people it reaches (local, national, international)"),
    Urgency("urgency", "Urgency", "whether it is unfolding now or needs action soon"),
    Interest("interest", "Interest", "how surprising, new or revealing it is"),
    Coverage("coverage", "Coverage", "how many sources report the same event"),
    Relevance("relevance", "Relevance", "whether it touches your tracked topics or region"),
}

enum class PriorityLevel(val key: String, val label: String) {
    Critical("critical", "Critical"),
    High("high", "High"),
    Notable("notable", "Notable"),
    Routine("routine", "Routine"),
}

data class BriefingStory(
    val id: String,
    val title: String,
    val link: String?,
    val source: String,
    val publishedAt: Long,
    val summary: String = "",
    val coverUrl: String? = null,
    /** "Why it matters" text written earlier, if any. */
    val impact: String? = null,
)

data class BriefingWindow(val startMs: Long, val endMs: Long)

/** Each criterion is scored 0 (none) to 3 (strong). */
typealias CriteriaScores = Map<Criterion, Int>

data class StoryRating(
    val scores: CriteriaScores,
    /** The concrete fact behind each score. */
    val notes: Map<Criterion, String>,
    val dimension: ImpactDimension,
    val impacts: Map<ImpactAngle, String> = emptyMap(),
    val whyItMatters: String? = null,
    val ratedByAi: Boolean = false,
)

/** What the AI returns per story. Coverage and relevance stay computed from data. */
data class AiStoryRating(
    val id: String,
    val scores: Map<Criterion, Int>,
    val whys: Map<Criterion, String>,
    val dimension: ImpactDimension,
    val impacts: Map<ImpactAngle, String>,
)

data class AiRatingCandidate(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val alsoReportedBy: List<String>,
)

data class RatedStory(
    val story: BriefingStory,
    /** Other reports of the same event. */
    val related: List<BriefingStory>,
    val rating: StoryRating,
    val score: Int,
    val level: PriorityLevel,
    /** Larger ongoing situation shared with other, different stories. */
    val storyline: String? = null,
    /** The AI's one sentence on how the storyline's stories connect. */
    val storylineWhy: String? = null,
    /** The AI's one sentence on why the folded reports are the same event. */
    val duplicateWhy: String? = null,
    /** Part of an event that began and was resolved within the briefing's period. */
    val resolution: StoryResolution? = null,
)

enum class ResolutionRole(val key: String) {
    Resolution("resolution"),
    Update("update"),
    Start("start"),
}

data class StoryResolution(
    val key: String,
    val label: String,
    /** One sentence from how it began to how it ended. */
    val summary: String,
    val role: ResolutionRole,
    /** Position from the first report (0) to the last. */
    val order: Int,
)

/** An event that began and ended within the period, as the AI found it: ids from first report to last. */
data class GroupingResolution(val ids: List<String>, val resolvedBy: String, val label: String, val summary: String)

data class GroupingItem(
    val id: String,
    val title: String,
    val summary: String,
    /** Ids whose headlines name the same people or things: a hint to check, not a verdict. */
    val maybeSameAs: List<String> = emptyList(),
)

data class StoryGrouping(
    /** Sets of ids that report the very same happening. */
    val duplicates: List<List<String>> = emptyList(),
    /** Why each set in [duplicates] is one happening, by position. */
    val duplicateWhy: List<String> = emptyList(),
    /** id to the label of the larger ongoing situation it belongs to. */
    val storylines: Map<String, String> = emptyMap(),
    /** Storyline label to how its stories connect. */
    val storylineWhy: Map<String, String> = emptyMap(),
    /** Events that began and ended within the period. */
    val resolutions: List<GroupingResolution> = emptyList(),
)

enum class DigestKind(val key: String) {
    Critical("critical"),
    Leading("leading"),
    Interesting("interesting"),
}

/** One line of the AI's summary at the top, and the stories it is about. */
data class DigestBullet(val kind: DigestKind, val text: String, val ids: List<String>)

data class DigestItem(
    val id: String,
    val title: String,
    val summary: String,
    val level: PriorityLevel,
    val score: Int,
    val area: String,
    val storyline: String,
    val sources: Int,
    val impacts: Map<ImpactAngle, String>,
    /** Set when the story began and ended within the period: how it went. */
    val resolved: String? = null,
)

data class BriefingRecord(
    val id: String,
    val title: String,
    val period: BriefingPeriod,
    val scope: String,
    val scheduled: Boolean,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val generatedAtMs: Long,
    val itemCount: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("period", period.key)
        .put("scope", scope)
        .put("scheduled", scheduled)
        .put("windowStartMs", windowStartMs)
        .put("windowEndMs", windowEndMs)
        .put("generatedAtMs", generatedAtMs)
        .put("itemCount", itemCount)

    companion object {
        fun fromJson(json: JSONObject): BriefingRecord? {
            val id = json.optString("id")
            if (!BriefingEngine.isValidId(id)) return null
            return BriefingRecord(
                id = id,
                title = json.optString("title"),
                period = BriefingPeriod.fromKey(json.optString("period")),
                scope = json.optString("scope", "All feeds"),
                scheduled = json.optBoolean("scheduled"),
                windowStartMs = json.optLong("windowStartMs"),
                windowEndMs = json.optLong("windowEndMs"),
                generatedAtMs = json.optLong("generatedAtMs"),
                itemCount = json.optInt("itemCount"),
            )
        }
    }
}

data class Briefing(val record: BriefingRecord, val html: String)

data class BriefingOptions(
    val period: BriefingPeriod,
    val scope: String,
    val window: BriefingWindow,
    val trackedTerms: List<String> = emptyList(),
    val localTerms: List<String> = emptyList(),
    val region: String = "",
    /** Language of the page and its notes; the AI prompts should use the same one. */
    val language: BriefingLanguage = BriefingLanguage.English,
    val generatedAtMs: Long = System.currentTimeMillis(),
)

object BriefingEngine {
    /** Default importance of each criterion. The briefing page lets the reader slide these. */
    val DEFAULT_WEIGHTS: Map<Criterion, Double> = mapOf(
        Criterion.Severity to 3.0,
        Criterion.Scale to 2.5,
        Criterion.Urgency to 2.0,
        Criterion.Interest to 1.5,
        Criterion.Coverage to 1.5,
        Criterion.Relevance to 1.0,
    )

    /** How far a single criterion at full score and full weight lifts a story on its own (0-1). */
    const val SINGLE_CRITERION_STRENGTH = 0.65
    const val CRITICAL_AT = 70
    const val HIGH_AT = 50
    const val NOTABLE_AT = 25

    /** Highest score a keyword-rated story can get when the AI rated the rest of the briefing. */
    const val UNCHECKED_SCORE_CAP = HIGH_AT - 1

    private val PRESETS: List<Pair<String, Map<Criterion, Double>>> = listOf(
        "Balanced" to DEFAULT_WEIGHTS,
        "Critical first" to weights(5.0, 3.0, 3.0, 0.5, 2.0, 1.0),
        "Interesting" to weights(1.0, 1.0, 0.5, 5.0, 1.0, 2.0),
        "Close to home" to weights(2.0, 1.0, 1.5, 1.5, 1.0, 5.0),
    )

    private fun weights(vararg values: Double): Map<Criterion, Double> =
        Criterion.entries.zip(values.toList()).toMap()

    // ---- rule-based rating (used when AI is off, and for coverage/relevance always) ----

    // Terms match the start of a word, so stems cover inflections ("атак" -> "атака", "атаки").
    // A trailing "$" means the whole word must match.
    private val DIMENSION_TERMS: List<Pair<ImpactDimension, List<String>>> = listOf(
        ImpactDimension.Security to listOf("war$", "wars$", "attack", "missile", "airstrike", "strikes$", "military", "army", "troops", "drone", "terror", "shooting", "explosion", "nato$", "ceasefire", "invasion", "refiner", "война", "атак", "ракет", "удар", "военн", "армия", "войск", "дрон", "терор", "стрелб", "взрив", "нато", "примири", "обстрел", "отбран", "сигурност"),
        ImpactDimension.Politics to listOf("election", "government", "parliament", "minister", "president", "protest", "sanction", "vote", "senate", "congress", "prosecutor", "избор", "правителств", "парламент", "министър", "президент", "протест", "санкци", "гласув", "прокурор", "депутат", "партия", "премиер"),
        ImpactDimension.Economy to listOf("inflation", "prices", "market", "bank", "rates$", "budget", "tax", "jobs$", "oil$", "gas$", "tariff", "euro$", "stocks", "assets", "инфлаци", "цени", "цена", "пазар", "банк", "лихв", "бюджет", "данъ", "петрол", "газ", "мита", "евро", "актив", "икономи", "заплат", "пенси", "имот"),
        ImpactDimension.Health to listOf("virus", "outbreak", "hospital", "disease", "vaccine", "pandemic", "cancer", "вирус", "огнищ", "болниц", "болест", "ваксин", "пандеми", "здрав", "нзок", "лекар"),
        ImpactDimension.Environment to listOf("earthquake", "flood", "wildfire", "storm", "climate", "drought", "hurricane", "collapse", "земетрес", "наводн", "пожар", "буря", "климат", "суша", "ураган", "срути", "бедстви"),
        ImpactDimension.Society to listOf("court", "arrest", "police", "crime", "murder", "accident", "fraud", "school", "students", "съд", "арест", "полици", "престъп", "убийств", "катастроф", "измам", "разслед", "стачк", "училищ", "студент", "граждан"),
        ImpactDimension.Technology to listOf("ai$", "cyber", "hack", "chip", "software", "satellite", "robot", "изкуствен", "кибер", "хакер", "чип", "сателит", "технолог"),
        ImpactDimension.Culture to listOf("film", "movie", "music", "festival", "actor", "episode", "series", "oscar", "филм", "музик", "фестивал", "актьор", "турне", "сериал", "трейлър", "концерт"),
        ImpactDimension.Sports to listOf("football", "match", "league", "championship", "playoff", "goal", "coach", "футбол", "мач", "лига", "шампионат", "титла", "треньор", "отбор", "класико", "волейбол", "клуб", "club", "mvp$", "реал$", "nba$", "nfl$", "olympic", "олимпи"),
    )

    private val SEVERITY_TERMS = listOf("killed", "killing", "dead$", "death", "deaths", "casualt", "injured", "emergency", "crisis", "collapse", "evacuat", "disaster", "catastroph", "victims", "убит", "загин", "жертв", "ранен", "извънредн", "криза", "срив", "евакуац", "бедстви", "катастроф", "смърт", "загуб")
    private val SCALE_TERMS = listOf("national", "nationwide", "country", "global", "world", "international", "european", "eu$", "nato$", "millions", "government", "parliament", "национал", "страната", "цялата", "светов", "международ", "европ", "ес$", "милион", "правителств", "парламент", "държав")
    private val URGENCY_TERMS = listOf("breaking", "urgent", "live$", "now$", "today", "imminent", "deadline", "warning", "alert", "resume", "извънредно", "спешно", "сега", "днес", "предупре", "тревог", "ултиматум", "възобнов", "незабавно")

    // Naming other countries or shared regions signals reach beyond one place.
    private val COUNTRY_TERMS = listOf("ukrain", "russia", "usa$", "us$", "america", "romania", "turkey", "türk", "greece", "serbia", "china", "iran", "israel", "gaza", "pakistan", "india", "germany", "france", "britain", "poland", "moldova", "nato$", "eu$", "black$", "украйн", "русия", "руск", "сащ", "америк", "румъни", "турци", "гърци", "сърби", "македони", "китай", "иран", "израел", "газа", "пакистан", "индия", "германи", "франци", "британ", "полша", "молдов", "нато", "ес$", "черно", "истанбул", "киев", "москва", "брюксел", "вашингтон", "оман")
    private val INTEREST_TERMS = listOf("first$", "record", "never", "rare$", "discover", "unusual", "secret", "mystery", "surpris", "unexpected", "breakthrough", "reveal", "първ", "рекорд", "никога", "рядк", "откри", "необичайн", "тайн", "мистери", "изненад", "невиждан", "уникал", "пробив", "разкри", "любопит")

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(WORD_SPLIT).filter { it.isNotEmpty() }

    /** The words in the text that match the terms, in term order. */
    private fun termHits(textWords: List<String>, terms: List<String>): List<String> =
        terms.mapNotNull { term ->
            val exact = term.endsWith("$")
            val stem = term.removeSuffix("$")
            textWords.firstOrNull { word -> if (exact) word == stem else word.startsWith(stem) }
        }

    /** The user's topics found in the text. Short topics ("AI", "ЕС") must match a whole word. */
    private fun trackedHits(textWords: List<String>, trackedTerms: List<String>): List<String> =
        trackedTerms.filter { term ->
            val parts = words(term)
            parts.isNotEmpty() && parts.all { part ->
                textWords.any { word -> if (part.length <= 4) word == part else word.startsWith(part.dropLast(1)) }
            }
        }

    /** A few words around [hitWord], taken from the title first and then the summary. */
    private fun quoteAround(story: BriefingStory, hitWord: String, radius: Int = 4): String {
        for (text in listOf(story.title, story.summary)) {
            val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val index = tokens.indexOfFirst { token -> hitWord in words(token) }
            if (index < 0) continue
            val from = (index - radius).coerceAtLeast(0)
            val to = (index + radius + 1).coerceAtMost(tokens.size)
            val prefix = if (from > 0) "…" else ""
            val suffix = if (to < tokens.size) "…" else ""
            return "“$prefix${tokens.subList(from, to).joinToString(" ")}$suffix”"
        }
        return "“$hitWord”"
    }

    private fun clamp(value: Int): Int = value.coerceIn(0, 3)

    /**
     * 0-100. Combined like independent chances, so one strong criterion is enough to make a
     * story matter (a deadly attack does not need to be surprising too), and each further
     * strong criterion lifts it more. An average would let the zeros drown the one big signal.
     * The briefing page runs the same formula in the browser.
     */
    fun scoreOf(scores: CriteriaScores, weights: Map<Criterion, Double> = DEFAULT_WEIGHTS): Int {
        val raw = rawScoreOf(scores, weights)
        return if (criticalCap(scores) != null) minOf(raw, CRITICAL_AT - 1) else raw
    }

    /** The score before [criticalCap], for saying why a story was kept below Critical. */
    fun rawScoreOf(scores: CriteriaScores, weights: Map<Criterion, Double> = DEFAULT_WEIGHTS): Int {
        val heaviest = Criterion.entries.maxOf { (weights[it] ?: 0.0).coerceAtLeast(0.0) }
        if (heaviest <= 0.0) return 0
        val unlifted = Criterion.entries.fold(1.0) { rest, key ->
            val lift = ((weights[key] ?: 0.0).coerceAtLeast(0.0) / heaviest) *
                (clamp(scores[key] ?: 0) / 3.0) * SINGLE_CRITERION_STRENGTH
            rest * (1 - lift)
        }
        return ((1 - unlifted) * 100).roundToInt()
    }

    enum class CriticalCap(val textKey: String) { NotNow("cappedNotNow"), Minor("cappedMinor") }

    /**
     * Critical is kept for what is happening now (or due within days) and is either severe or
     * reaches a whole country. A killing under investigation today qualifies even when local;
     * a background feature about a long-running situation, however grim, is High at most.
     * The briefing page runs the same rule.
     */
    fun criticalCap(scores: CriteriaScores): CriticalCap? = when {
        clamp(scores[Criterion.Urgency] ?: 0) < 2 -> CriticalCap.NotNow
        clamp(scores[Criterion.Severity] ?: 0) < 3 && clamp(scores[Criterion.Scale] ?: 0) < 2 -> CriticalCap.Minor
        else -> null
    }

    fun levelOf(score: Int): PriorityLevel = when {
        score >= CRITICAL_AT -> PriorityLevel.Critical
        score >= HIGH_AT -> PriorityLevel.High
        score >= NOTABLE_AT -> PriorityLevel.Notable
        else -> PriorityLevel.Routine
    }

    private fun distinctSources(stories: List<BriefingStory>): List<String> =
        stories.map { it.source.trim() }.filter { it.isNotEmpty() }.distinct()

    /** "Al Jazeera – Breaking News, World News…" -> "Al Jazeera". */
    fun shortSource(source: String): String = source.split(Regex("\\s+[–—|-]\\s+")).first().trim()

    private fun coverageNote(names: List<String>, language: BriefingLanguage): String {
        val t = BriefingText.of(language)
        return if (names.size > 1) t.reportedBy(names.joinToString(", ")) else t.onlyOne(names.firstOrNull() ?: t.oneSource)
    }

    private fun coverageScore(sourceCount: Int): Int = when {
        sourceCount >= 4 -> 3
        sourceCount == 3 -> 2
        sourceCount == 2 -> 1
        else -> 0
    }

    fun ruleRating(
        story: BriefingStory,
        related: List<BriefingStory>,
        window: BriefingWindow,
        trackedTerms: List<String> = emptyList(),
        localTerms: List<String> = emptyList(),
        region: String = "",
        language: BriefingLanguage = BriefingLanguage.English,
    ): StoryRating {
        val t = BriefingText.of(language)
        val textWords = words("${story.title} ${story.summary}")
        // Sports and entertainment words are unambiguous context, while words like "war" or
        // "attack" are often used as metaphors there, so their hits count double.
        var dimension = ImpactDimension.Other
        var bestHits = 0
        for ((candidate, terms) in DIMENSION_TERMS) {
            val factor = if (candidate == ImpactDimension.Sports || candidate == ImpactDimension.Culture) 2 else 1
            val hits = termHits(textWords, terms).size * factor
            if (hits > bestHits) {
                bestHits = hits
                dimension = candidate
            }
        }
        val lightweight = dimension == ImpactDimension.Sports || dimension == ImpactDimension.Culture
        val notes = mutableMapOf<Criterion, String>()

        val severityHits = termHits(textWords, SEVERITY_TERMS)
        var severity = when {
            severityHits.size >= 2 -> 3
            severityHits.size == 1 -> 2
            else -> 0
        }
        if (severity > 0) notes[Criterion.Severity] = quoteAround(story, severityHits.first())
        if (severity == 0 && dimension in setOf(ImpactDimension.Security, ImpactDimension.Health, ImpactDimension.Environment)) {
            severity = 1
            notes[Criterion.Severity] = t.noHarmYet(t.dimensions.getValue(dimension))
        }
        // Sports and entertainment borrow dramatic words ("war", "attack") without real-world harm.
        if (lightweight && severity > 1) {
            severity = 1
            notes[Criterion.Severity] = t.figureOfSpeech(quoteAround(story, severityHits.first()), t.dimensions.getValue(dimension))
        }

        val scaleHits = termHits(textWords, SCALE_TERMS)
        val countryHits = termHits(textWords, COUNTRY_TERMS)
        val reach = (scaleHits.size + if (countryHits.size >= 2) 2 else countryHits.size).coerceAtMost(3)
        val scale = if (lightweight) reach.coerceAtMost(1) else reach
        if (scale > 0) {
            notes[Criterion.Scale] = if (countryHits.size >= 2) {
                t.severalCountries(countryHits.take(3).joinToString(", ") { "“$it”" })
            } else {
                quoteAround(story, scaleHits.firstOrNull() ?: countryHits.first())
            }
        }

        val urgencyHits = termHits(textWords, URGENCY_TERMS)
        val late = story.publishedAt >= window.endMs - (window.endMs - window.startMs) * 3 / 10
        val urgency = (urgencyHits.size.coerceAtMost(2) + if (late && urgencyHits.isNotEmpty()) 1 else 0).coerceAtMost(3)
        if (urgency > 0) notes[Criterion.Urgency] = quoteAround(story, urgencyHits.first())

        val interestHits = termHits(textWords, INTEREST_TERMS)
        val interest = (interestHits.size * 2).coerceAtMost(3)
        if (interest > 0) notes[Criterion.Interest] = quoteAround(story, interestHits.first())

        // Zero scores get a reason too, so the reader sees what was looked for and not found.
        if (severity == 0) notes[Criterion.Severity] = t.zero.getValue(Criterion.Severity)
        if (scale == 0) notes[Criterion.Scale] = t.zero.getValue(Criterion.Scale)
        if (urgency == 0) notes[Criterion.Urgency] = t.zero.getValue(Criterion.Urgency)
        if (interest == 0) notes[Criterion.Interest] = t.zero.getValue(Criterion.Interest)

        val sources = distinctSources(listOf(story) + related).map(::shortSource)
        val coverage = coverageScore(sources.size)
        notes[Criterion.Coverage] = coverageNote(sources, language)

        val topics = trackedHits(textWords, trackedTerms)
        val localHits = termHits(textWords, localTerms)
        val relevance = ((if (topics.isNotEmpty()) 1 else 0) + (if (topics.size > 1) 1 else 0) +
            (if (localHits.isNotEmpty()) 1 else 0)).coerceAtMost(3)
        val relevanceParts = buildList {
            if (topics.isNotEmpty()) {
                add("${t.yourTopics(topics.size)} " + topics.take(3).joinToString(", ") { "“$it”" })
            }
            if (localHits.isNotEmpty()) add("${region.ifBlank { t.yourRegion }} ${quoteAround(story, localHits.first(), 2)}")
        }
        if (relevanceParts.isNotEmpty()) {
            notes[Criterion.Relevance] = t.mentions(relevanceParts)
        } else if (trackedTerms.isNotEmpty() || region.isNotBlank()) {
            notes[Criterion.Relevance] = t.noneMentioned(trackedTerms.size, region)
        }

        return StoryRating(
            scores = mapOf(
                Criterion.Severity to severity,
                Criterion.Scale to scale,
                Criterion.Urgency to urgency,
                Criterion.Interest to interest,
                Criterion.Coverage to coverage,
                Criterion.Relevance to relevance,
            ),
            notes = notes,
            dimension = dimension,
            whyItMatters = story.impact,
        )
    }

    // ---- grouping the same event from several sources ----

    private val LATIN_OF: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sht", 'ъ' to "a",
        'ь' to "y", 'ю' to "yu", 'я' to "ya",
    )
    private val VOWELS = Regex("[aeiouy]")
    private val DOUBLED = Regex("(.)\\1+")

    /**
     * A key that survives spelling and script: Latin letters, vowels and doubled letters
     * dropped, first four consonants. "Тесла" and "Tesla" both give "tsl", "Роудстър" and
     * "Roadster" both "rdst", "strikes" and "strike" both "strk".
     */
    fun wordKey(word: String): String {
        val latin = word.lowercase(Locale.ROOT).map { letter -> LATIN_OF[letter] ?: letter.toString() }.joinToString("")
        return latin.replace(VOWELS, "").replace(DOUBLED, "$1").take(4)
    }

    private class TitleKeys(val keys: Set<String>, val names: Set<String>)

    /**
     * The title's word keys, and separately the names in it: capitalised words after the
     * first. A title in Title Case has no usable names, since every word is capitalised.
     */
    private fun titleKeys(title: String): TitleKeys {
        val tokens = title.split(WORD_SPLIT).filter { it.isNotEmpty() }
        fun capitalised(token: String) = token.first().isUpperCase()
        val titleCase = tokens.count { it.length > 1 && capitalised(it) } > tokens.size / 2.0
        val keys = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        tokens.forEachIndexed { index, token ->
            val key = wordKey(token)
            if (key.length < 3) return@forEachIndexed
            val name = !titleCase && index > 0 && capitalised(token)
            if (token.length >= 4 || name) keys.add(key)
            if (name) names.add(key)
        }
        return TitleKeys(keys, names)
    }

    /**
     * Whether two headlines probably report the same event, by the grouping rule below: most
     * of their words in common, or two names plus one more word. It is a guess, so whoever
     * uses the match should be able to tell an unrelated story apart.
     */
    fun sameEventTitles(a: String, b: String): Boolean {
        val one = titleKeys(a)
        val two = titleKeys(b)
        val shared = one.keys.count { it in two.keys }
        val union = (one.keys + two.keys).size.coerceAtLeast(1)
        return (shared >= 2 && shared.toDouble() / union >= 0.35) || (one.names.count { it in two.names } >= 2 && shared >= 3)
    }

    /**
     * Groups stories whose titles report the same event; the most informative one leads.
     * [loose] also merges titles that only share names, which is a guess. [exactOnly] merges
     * only the same link or title: when the AI groups the briefing it gets the word matches
     * as hints and decides, since "boy missing near Varna" and "boy found near Varna" share
     * most words but are the start and the end of one event.
     */
    fun clusterStories(
        stories: List<BriefingStory>,
        loose: Boolean = true,
        exactOnly: Boolean = false,
    ): List<Pair<BriefingStory, List<BriefingStory>>> {
        class Cluster(val members: MutableList<BriefingStory>, val keys: MutableSet<String>, val names: MutableSet<String>)
        val clusters = mutableListOf<Cluster>()
        val seenLinks = mutableSetOf<String>()
        for (story in stories.sortedBy { it.publishedAt }) {
            val key = (story.link?.takeIf { it.isNotBlank() } ?: story.title).trim().lowercase(Locale.ROOT)
            if (key.isEmpty() || !seenLinks.add(key)) continue
            val title = titleKeys(story.title)
            val match = if (exactOnly) null else clusters.firstOrNull { cluster ->
                val shared = title.keys.count { it in cluster.keys }
                val union = (title.keys + cluster.keys).size.coerceAtLeast(1)
                // Most of the words in common, or the same two people or things named plus one more
                // word: "Костов, Борисов, Петков и Денков призоваха…" and "Костов, Борисов и Петков: Мирът…"
                // quote different lines of one joint statement.
                (shared >= 2 && shared.toDouble() / union >= 0.35) ||
                    (loose && title.names.count { it in cluster.names } >= 2 && shared >= 3)
            }
            if (match != null) {
                match.members.add(story)
                match.keys.addAll(title.keys)
                match.names.addAll(title.names)
            } else {
                clusters.add(Cluster(mutableListOf(story), title.keys.toMutableSet(), title.names.toMutableSet()))
            }
        }
        fun informative(story: BriefingStory) = (if (story.summary.isNotBlank()) 2 else 0) + (if (story.coverUrl != null) 1 else 0)
        return clusters.map { cluster ->
            val lead = cluster.members.sortedWith(compareByDescending<BriefingStory> { informative(it) }.thenBy { it.publishedAt }).first()
            lead to cluster.members.filter { it !== lead }
        }
    }

    /** Sets of ids the loose title rule thinks are the same event, as hints or a fallback. */
    fun possibleDuplicates(rated: List<RatedStory>): List<List<String>> =
        clusterStories(rated.map { it.story }, loose = true)
            .filter { (_, related) -> related.isNotEmpty() }
            .map { (lead, related) -> listOf(lead.id) + related.map { it.id } }

    // ---- AI rating ----

    /**
     * Headlines speak in figures of speech ("Черно море удари българската реколта"); taken
     * literally they turn a sea into the one blocking grain exports. Every prompt that writes
     * text for the reader carries this rule. The same text as the Mac widget's.
     */
    const val LITERAL_FACTS_RULE =
        "Say who or what actually does each thing. Headlines use figures of speech: a sea, a market, a city or a country name " +
            "often stands for people, ships, a government or a risk (\"the Black Sea hit the harvest\" means ships avoid the Black " +
            "Sea, not that the sea acts). Never repeat such a figure as a fact. If the text does not say who or what causes " +
            "something, describe what is known (ships refuse to come to Bulgarian ports) and do not invent or guess a cause."

    fun buildAiRatingPrompt(candidates: List<AiRatingCandidate>, bulgarian: Boolean, region: String): String {
        val items = JSONArray()
        candidates.forEach { candidate ->
            items.put(
                JSONObject()
                    .put("id", candidate.id)
                    .put("title", candidate.title)
                    .put("summary", candidate.summary)
                    .put("source", candidate.source)
                    .put("alsoReportedBy", JSONArray(candidate.alsoReportedBy)),
            )
        }
        return listOf(
            "You are a news editor rating how much each story matters to an ordinary reader in ${region.ifBlank { "their country" }}.",
            "Use only each headline and summary. Be specific to each story: every explanation must name the concrete fact behind it",
            "(a number, place, person, decision, amount or date). Never write generic phrases such as \"significant impact\",",
            "\"important development\", \"affects many people\" or \"relevant to readers\".",
            LITERAL_FACTS_RULE,
            "Score four criteria from 0 (none) to 3 (strong):",
            "- severity: ${Criterion.Severity.text}. Sports, celebrity and entertainment score 0-1 even with dramatic words like \"war\" or \"attack\".",
            "- scale: 1 one town, group or province (also one far away), 2 a whole country, 3 several countries or the world.",
            "- urgency: 3 happening now or a deadline within days, 2 this week, 1 slow-moving, 0 none. Features and background pieces about a long-running situation score 0-1.",
            "- interest: how surprising, new or revealing the story is as a whole (a first, a record, an unexpected twist, a hidden fact). Routine statements and scheduled events score 0-1.",
            "  interestWhy names the most surprising thing about the story, which is usually its main claim in the headline",
            "  (a car that flies, not its acceleration). Use a side detail or a number only when that detail is itself the surprise.",
            "  The summary adds details the headline leaves out; read both before deciding what the story is about.",
            "For each criterion add \"<criterion>Why\": at most 15 words naming the fact, e.g. \"29 pupils killed when a school roof fell in Khyber Pakhtunkhwa\".",
            "Fill it for low scores too, saying what this story lacks, e.g. severityWhy \"A TV casting change; no one is harmed and no money is at stake\".",
            "Pick one dimension from: ${ImpactDimension.entries.joinToString(", ") { it.key }}.",
            "Write every text field in ${if (bulgarian) "Bulgarian" else "English"}.",
            "Return strict JSON only: {\"ratings\":[{\"id\":\"...\",\"severity\":0,\"severityWhy\":\"...\",\"scale\":0,\"scaleWhy\":\"...\",\"urgency\":0,\"urgencyWhy\":\"...\",\"interest\":0,\"interestWhy\":\"...\",\"dimension\":\"economy\"}]}",
            "Include every candidate exactly once.",
            "Candidates: $items",
        ).joinToString("\n")
    }

    private fun jsonObjectIn(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private val AI_CRITERIA = listOf(Criterion.Severity, Criterion.Scale, Criterion.Urgency, Criterion.Interest)

    fun parseAiRatings(text: String): List<AiStoryRating> {
        val list = jsonObjectIn(text)?.optJSONArray("ratings") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            if (id.isEmpty()) return@mapNotNull null
            val rawImpacts = item.optJSONObject("impacts")
            AiStoryRating(
                id = id,
                scores = AI_CRITERIA.associateWith { clamp(item.optInt(it.key, 0)) },
                whys = AI_CRITERIA.associateWith { item.optString("${it.key}Why").trim().take(200) },
                dimension = ImpactDimension.fromKey(item.optString("dimension")),
                impacts = ImpactAngle.entries.mapNotNull { angle ->
                    rawImpacts?.optString(angle.key)?.trim()?.take(300)?.takeIf { it.isNotEmpty() }?.let { angle to it }
                }.toMap(),
            )
        }
    }

    /** Ratings in the same JSON the AI returns, so parseAiRatings reads them back. */
    fun ratingsJson(ratings: List<AiStoryRating>): String {
        val list = org.json.JSONArray()
        ratings.forEach { rating ->
            val item = JSONObject().put("id", rating.id).put("dimension", rating.dimension.key)
            AI_CRITERIA.forEach { key ->
                item.put(key.key, rating.scores[key] ?: 0).put("${key.key}Why", rating.whys[key].orEmpty())
            }
            list.put(item)
        }
        return JSONObject().put("ratings", list).toString()
    }

    private fun applyAiRating(rule: StoryRating, ai: AiStoryRating): StoryRating =
        rule.copy(
            scores = rule.scores + ai.scores,
            notes = rule.notes + AI_CRITERIA.mapNotNull { key ->
                (ai.whys[key]?.takeIf { it.isNotBlank() } ?: rule.notes[key])?.let { key to it }
            },
            dimension = ai.dimension,
            impacts = ai.impacts,
            ratedByAi = true,
        )

    /**
     * One short call over all the headlines at once. Duplicates come back as explicit lists
     * of ids, not shared labels: separately written labels such as "military strike" match
     * by accident and glue unrelated stories together. Each group carries the AI's reason,
     * which the page shows, so a wrong group is easy to spot.
     */
    fun buildGroupingPrompt(items: List<GroupingItem>, language: BriefingLanguage): String {
        val json = JSONArray()
        items.forEach { item ->
            val entry = JSONObject().put("id", item.id).put("title", item.title).put("summary", item.summary)
            if (item.maybeSameAs.isNotEmpty()) entry.put("maybeSameAs", JSONArray(item.maybeSameAs))
            json.put(entry)
        }
        return listOf(
            "You are a news editor. You are given news items (id, headline, summary). Read what each one is actually about,",
            "who does what, where and when, and then do two things.",
            LITERAL_FACTS_RULE,
            "1. \"duplicates\": sets of ids that report the SAME happening: one announcement, statement, decision, incident,",
            "   product reveal or match, covered by different outlets. They are duplicates even when the headlines quote",
            "   different lines of it, use different words, another language or another spelling of a name (Tesla / Тесла,",
            "   Roadster / Роудстър). A joint statement by the same people reported twice is one happening. Two different",
            "   strikes, statements by one person on different subjects, or two events in the same war are NOT duplicates.",
            "   A report that something ended (found, freed, reopened, caught) is NOT a duplicate of the report that it began;",
            "   those go under \"resolutions\".",
            "   \"maybeSameAs\" lists items whose headlines name the same people or things. Check each of them against the",
            "   summaries and decide; sharing names alone does not make two items one happening.",
            "   For each set add \"why\": one sentence naming the shared happening, e.g. \"Both report the four former prime",
            "   ministers' joint call on Radev over national security\".",
            "2. \"storylines\": larger ongoing situations that at least two DIFFERENT happenings belong to, for example a drone",
            "   strike on a ship, a naval command offer and a moratorium proposal that are all about Black Sea security.",
            "   A storyline is ONE specific situation with the same main actors or place (one war, one crisis, one negotiation),",
            "   never a broad theme: \"military conflicts in the region\", \"the economy\" or \"world politics\" are not storylines,",
            "   and strikes in the Persian Gulf and a grain blockade in the Black Sea are two different situations.",
            "   The label names the situation itself (who or where), e.g. \"Iran's strikes on US bases\".",
            "   Put an item in a storyline only if its subject IS that situation. A sports, celebrity or entertainment item",
            "   never joins a war, politics or economy storyline, even when it uses words like \"war\", \"attack\" or \"drama\".",
            "   Give each a 2-5 word label, a \"why\" sentence saying how its items connect, and the ids that belong to it.",
            "   An item can be in at most one storyline. Most items belong to none.",
            "3. \"resolutions\": events that began AND were resolved within these items: a later item ends what an earlier one",
            "   reported (a missing person found, hostages freed, a fire contained, a closed road or airport reopened, a strike",
            "   called off, a suspect caught). List the ids from the first report to the last, say which id resolves it",
            "   (\"resolvedBy\"), give a 2-5 word label and a \"summary\": one sentence from how it began to how it ended, with",
            "   the times or facts the items give. Only when the later item clearly ends it; an update that does not end it is",
            "   not a resolution. Items in a resolution are in no storyline.",
            "Write labels and reasons in ${if (language == BriefingLanguage.Bulgarian) "Bulgarian" else "English"}.",
            "Return strict JSON only: {\"duplicates\":[{\"ids\":[\"id\",\"id\"],\"why\":\"...\"}],\"storylines\":[{\"label\":\"...\",\"why\":\"...\",\"ids\":[\"id\",\"id\"]}],\"resolutions\":[{\"ids\":[\"first\",\"last\"],\"resolvedBy\":\"last\",\"label\":\"...\",\"summary\":\"...\"}]}",
            "Items: $json",
        ).joinToString("\n")
    }

    fun parseGrouping(text: String): StoryGrouping {
        val json = jsonObjectIn(text) ?: return StoryGrouping()
        fun ids(array: JSONArray?): List<String> =
            if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf { id -> id.isNotEmpty() } }
        val duplicates = mutableListOf<List<String>>()
        val duplicateWhy = mutableListOf<String>()
        json.optJSONArray("duplicates")?.let { sets ->
            for (index in 0 until sets.length()) {
                // Either {"ids": [...], "why": "..."} or a bare list of ids.
                val asObject = sets.optJSONObject(index)
                val members = ids(asObject?.optJSONArray("ids") ?: sets.optJSONArray(index)).distinct()
                // A long list is the model lumping a whole topic together, not one happening.
                if (members.size !in 2..6) continue
                duplicates.add(members)
                duplicateWhy.add(asObject?.optString("why")?.trim()?.take(240).orEmpty())
            }
        }
        val storylines = mutableMapOf<String, String>()
        val storylineWhy = mutableMapOf<String, String>()
        json.optJSONArray("storylines")?.let { lines ->
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                val label = line.optString("label").trim().take(60)
                if (label.isEmpty()) continue
                line.optString("why").trim().take(240).takeIf { it.isNotEmpty() }?.let { storylineWhy[label] = it }
                ids(line.optJSONArray("ids")).forEach { id -> storylines.putIfAbsent(id, label) }
            }
        }
        val resolutions = mutableListOf<GroupingResolution>()
        json.optJSONArray("resolutions")?.let { arcs ->
            for (index in 0 until arcs.length()) {
                val arc = arcs.optJSONObject(index) ?: continue
                val members = ids(arc.optJSONArray("ids")).distinct()
                val label = arc.optString("label").trim().take(60)
                if (members.size !in 2..8 || label.isEmpty()) continue
                val named = arc.optString("resolvedBy").trim().take(200)
                resolutions.add(
                    GroupingResolution(
                        ids = members,
                        resolvedBy = if (named in members) named else members.last(),
                        label = label,
                        summary = arc.optString("summary").trim().take(300),
                    ),
                )
            }
        }
        return StoryGrouping(duplicates, duplicateWhy, storylines, storylineWhy, resolutions)
    }

    private fun labelKey(label: String): String = words(label).joinToString(" ")

    /** Coverage counts every source that reported the event, including folded reports. */
    private fun withCoverage(entry: RatedStory, aiRan: Boolean, language: BriefingLanguage): RatedStory {
        val sources = distinctSources(listOf(entry.story) + entry.related).map(::shortSource)
        val scores = entry.rating.scores + (Criterion.Coverage to coverageScore(sources.size))
        val notes = entry.rating.notes + (Criterion.Coverage to coverageNote(sources, language))
        val raw = scoreOf(scores)
        val score = if (aiRan && !entry.rating.ratedByAi) minOf(raw, UNCHECKED_SCORE_CAP) else raw
        return entry.copy(rating = entry.rating.copy(scores = scores, notes = notes), score = score, level = levelOf(score))
    }

    /**
     * Folds each set of duplicate reports into its strongest story (the others become the
     * "more reports" rows), then marks storylines that at least two remaining stories share.
     */
    fun applyGrouping(
        rated: List<RatedStory>,
        grouping: StoryGrouping,
        aiRan: Boolean = false,
        language: BriefingLanguage = BriefingLanguage.English,
    ): List<RatedStory> {
        val byId = rated.associateBy { it.story.id }
        val absorbed = mutableSetOf<String>()
        val extraRelated = mutableMapOf<String, List<BriefingStory>>()
        val reasons = mutableMapOf<String, String>()
        grouping.duplicates.forEachIndexed { index, set ->
            val members = set.mapNotNull { byId[it] }.filter { it.story.id !in absorbed }
            if (members.size < 2) return@forEachIndexed
            val sorted = members.sortedWith(compareByDescending<RatedStory> { it.score }.thenByDescending { it.story.publishedAt })
            val lead = sorted.first()
            for (other in sorted.drop(1)) {
                absorbed.add(other.story.id)
                extraRelated[lead.story.id] = extraRelated[lead.story.id].orEmpty() + other.story + other.related
            }
            grouping.duplicateWhy.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { reasons.putIfAbsent(lead.story.id, it) }
        }
        var kept = rated
            .filter { it.story.id !in absorbed }
            .map { entry ->
                extraRelated[entry.story.id]?.let {
                    withCoverage(entry.copy(related = entry.related + it, duplicateWhy = reasons[entry.story.id]), aiRan, language)
                } ?: entry
            }
        // An event that began and ended in the period: its reports become one group, the
        // resolution first. A report folded as a duplicate counts as the story it was folded into.
        val leadOf = mutableMapOf<String, String>()
        kept.forEach { entry -> (listOf(entry.story) + entry.related).forEach { leadOf.putIfAbsent(it.id, entry.story.id) } }
        val arcs = mutableMapOf<String, StoryResolution>()
        grouping.resolutions.forEachIndexed { arcIndex, arc ->
            val members = mutableListOf<String>()
            arc.ids.forEach { id -> leadOf[id]?.let { lead -> if (lead !in members && lead !in arcs) members.add(lead) } }
            if (members.size < 2) return@forEachIndexed
            val resolver = leadOf[arc.resolvedBy]
            val key = "resolution-$arcIndex-${labelKey(arc.label)}"
            members.forEachIndexed { order, id ->
                val role = when {
                    id == resolver || (resolver !in members && order == members.size - 1) -> ResolutionRole.Resolution
                    order == 0 -> ResolutionRole.Start
                    else -> ResolutionRole.Update
                }
                arcs[id] = StoryResolution(key, arc.label, arc.summary, role, order)
            }
        }
        kept = kept.map { entry -> arcs[entry.story.id]?.let { entry.copy(resolution = it) } ?: entry }
        fun lineOf(entry: RatedStory) = if (entry.resolution != null) "" else labelKey(grouping.storylines[entry.story.id].orEmpty())
        val members = kept.filter { lineOf(it).isNotEmpty() }.groupBy(::lineOf)
        // A safety net under the AI: a sports or entertainment story stays in a storyline only
        // beside another of its kind, so a football row never sits inside a war storyline.
        fun fits(entry: RatedStory): Boolean {
            val dimension = entry.rating.dimension
            if (dimension != ImpactDimension.Sports && dimension != ImpactDimension.Culture) return true
            return members[lineOf(entry)].orEmpty().any { it !== entry && it.rating.dimension == dimension }
        }
        val counts = kept.filter { lineOf(it).isNotEmpty() && fits(it) }.groupingBy(::lineOf).eachCount()
        return kept.map { entry ->
            val label = grouping.storylines[entry.story.id].orEmpty()
            if (entry.resolution != null || !fits(entry) || (counts[labelKey(label)] ?: 0) < 2) entry
            else entry.copy(storyline = label, storylineWhy = grouping.storylineWhy[label])
        }
    }

    // ---- the editor's summary at the top ----

    /**
     * One call over the stories the briefing shows, after rating and grouping: the AI reads
     * them together and writes what matters, joining stories about one situation into one
     * line instead of listing headlines.
     */
    fun buildDigestPrompt(items: List<DigestItem>, language: BriefingLanguage, region: String): String {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("summary", item.summary)
                    .put("level", item.level.key)
                    .put("score", item.score)
                    .put("area", item.area)
                    .put("storyline", item.storyline)
                    .put("sources", item.sources)
                    .put("impacts", JSONObject().also { impacts -> item.impacts.forEach { (angle, text) -> impacts.put(angle.key, text) } })
                    .also { entry -> item.resolved?.let { entry.put("resolved", it) } },
            )
        }
        return listOf(
            "You are the editor of a short news briefing for a reader in ${region.ifBlank { "their country" }}.",
            "Below are the stories it covers, strongest first, each with its rating level, score (0-100), area, storyline,",
            "and number of sources. Write 3 to 6 bullets that tell the reader what matters in this period.",
            "- Start with what is critical, then what leads. Put stories about the same situation in one bullet and say how",
            "  they connect. Never join unrelated stories; a sports or entertainment story never shares a bullet with a war,",
            "  politics or economy story.",
            "- Each bullet is one or two plain sentences, at most 40 words: what happened, with the concrete fact (who, what,",
            "  number, place), and what it means for the reader. Do not just repeat the headline. No generic phrases such as",
            "  \"significant development\" or \"affects many people\".",
            "- $LITERAL_FACTS_RULE The story summaries below may themselves repeat a headline's figure of speech; do not copy it.",
            "- A story with \"resolved\" began and ended within this period: give it one bullet that says how it ended.",
            "- Finish with one bullet of kind \"interesting\" for the most surprising or unusual story not covered above, if any.",
            "- kind: \"critical\" for bullets about stories rated critical, \"leading\" for the other important ones, \"interesting\" for the last.",
            "- ids: the ids of the stories the bullet is about.",
            "Write in ${if (language == BriefingLanguage.Bulgarian) "Bulgarian" else "English"}.",
            "Return strict JSON only: {\"bullets\":[{\"kind\":\"critical\",\"text\":\"...\",\"ids\":[\"id\"]}]}",
            "Stories: $json",
        ).joinToString("\n")
    }

    fun parseDigest(text: String): List<DigestBullet> {
        val list = jsonObjectIn(text)?.optJSONArray("bullets") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val line = item.optString("text").trim().take(400)
            if (line.isEmpty()) return@mapNotNull null
            val kind = when (item.optString("kind")) {
                DigestKind.Critical.key -> DigestKind.Critical
                DigestKind.Interesting.key -> DigestKind.Interesting
                else -> DigestKind.Leading
            }
            val ids = item.optJSONArray("ids")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf { id -> id.isNotEmpty() } }
            }.orEmpty()
            DigestBullet(kind, line, ids)
        }.take(7)
    }

    // ---- picking a varied list ----

    private val byScore = compareByDescending<RatedStory> { it.score }.thenByDescending { it.story.publishedAt }

    /**
     * Highest score first, but no dimension takes more than ~30% of the list.
     * The cap is relaxed only when nothing else is left. The briefing page runs the
     * same rule in the browser when the reader moves the sliders.
     */
    fun pickVaried(rated: List<RatedStory>, limit: Int): List<RatedStory> {
        val dimensionCap = maxOf(2, ceil(limit * 0.3).toInt())
        val picked = mutableListOf<RatedStory>()
        val perDimension = mutableMapOf<ImpactDimension, Int>()
        val overflow = mutableListOf<RatedStory>()
        for (entry in rated.sortedWith(byScore)) {
            if (picked.size >= limit) break
            val count = perDimension[entry.rating.dimension] ?: 0
            if (count >= dimensionCap) {
                overflow.add(entry)
                continue
            }
            perDimension[entry.rating.dimension] = count + 1
            picked.add(entry)
        }
        for (entry in overflow) {
            if (picked.size >= limit) break
            picked.add(entry)
        }
        return picked.sortedWith(byScore)
    }

    // ---- windows and titles ----

    /** The period that ends now: an hour, day, week or month, or [customHours] for a custom one. */
    fun rollingWindow(period: BriefingPeriod, nowMs: Long, customHours: Int? = null): BriefingWindow {
        val hours = period.hours ?: (customHours ?: 24).coerceIn(1, BriefingPeriod.MAX_CUSTOM_HOURS)
        return BriefingWindow(nowMs - hours * HOUR_MS, nowMs)
    }

    data class BriefingSize(val limit: Int, val pageSize: Int)

    /**
     * How many stories a briefing shows and keeps for the sliders, growing with its length:
     * an hour shows 12 of 36, a day 20 of 60, a week 25 of 70, a month or more 30 of 80.
     */
    fun briefingSize(window: BriefingWindow): BriefingSize {
        val hours = (window.endMs - window.startMs).toDouble() / HOUR_MS
        return when {
            hours <= 1.5 -> BriefingSize(12, 36)
            hours <= 36 -> BriefingSize(20, 60)
            hours <= 24 * 8 -> BriefingSize(25, 70)
            else -> BriefingSize(30, 80)
        }
    }

    /** Longer than a day: story times need their date. */
    private fun spansDays(window: BriefingWindow): Boolean = window.endMs - window.startMs > 26 * HOUR_MS

    /** Stories past this many are ranked by the keyword rules alone before anything else. */
    private const val PRE_RANK_LIMIT = 2500

    private const val HOUR_MS = 60L * 60 * 1000

    private fun formatTime(ms: Long, language: BriefingLanguage = BriefingLanguage.English): String =
        SimpleDateFormat("HH:mm", language.locale).format(Date(ms))

    private fun formatDay(ms: Long, language: BriefingLanguage): String =
        SimpleDateFormat("EEE d MMM", language.locale).format(Date(ms))

    fun briefingTitle(
        period: BriefingPeriod,
        scope: String,
        window: BriefingWindow,
        language: BriefingLanguage = BriefingLanguage.English,
    ): String {
        fun day(ms: Long) = formatDay(ms, language)
        fun time(ms: Long) = formatTime(ms, language)
        val hours = (window.endMs - window.startMs).toDouble() / HOUR_MS
        val whenText = when {
            hours > 72 -> "${day(window.startMs)} – ${day(window.endMs)}"
            hours > 1.5 -> "${day(window.startMs)} ${time(window.startMs)} – ${day(window.endMs)} ${time(window.endMs)}"
            else -> "${day(window.startMs)}, ${time(window.startMs)}–${time(window.endMs)}"
        }
        return "${BriefingText.of(language).title(period)} · $scope · $whenText"
    }

    // ---- HTML ----

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun safeUrl(link: String?): String {
        val value = link?.trim().orEmpty()
        return if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) value else ""
    }

    /** JSON that is safe to place inside a <script> element. */
    private fun scriptJson(json: JSONObject): String = json.toString()
        .replace("<", "\\u003c")
        .replace(" ", "\\u2028")
        .replace(" ", "\\u2029")

    private fun Map<Criterion, Double>.toWeightsJson(): JSONObject =
        JSONObject().also { json -> Criterion.entries.forEach { json.put(it.key, this[it] ?: 0.0) } }

    private fun pageStory(entry: RatedStory, language: BriefingLanguage, withDate: Boolean = false): JSONObject {
        fun whenOf(ms: Long) = if (withDate) "${formatDay(ms, language)} ${formatTime(ms, language)}" else formatTime(ms, language)
        val story = entry.story
        val rating = entry.rating
        val scores = JSONObject().also { json -> Criterion.entries.forEach { json.put(it.key, clamp(rating.scores[it] ?: 0)) } }
        val notes = JSONObject().also { json -> rating.notes.forEach { (key, note) -> json.put(key.key, note) } }
        val impacts = JSONObject().also { json -> rating.impacts.forEach { (angle, text) -> json.put(angle.key, text) } }
        val related = JSONArray()
        entry.related.sortedBy { it.publishedAt }.forEach { other ->
            related.put(
                JSONObject()
                    .put("title", other.title)
                    .put("source", shortSource(other.source))
                    .put("time", whenOf(other.publishedAt))
                    .put("link", safeUrl(other.link)),
            )
        }
        return JSONObject()
            .put("title", story.title)
            .put("link", safeUrl(story.link))
            .put("source", shortSource(story.source))
            .put("time", whenOf(story.publishedAt))
            .put("publishedMs", story.publishedAt)
            // Older stored summaries may predate the clean-up at storage time.
            .put("summary", SummaryText.cleanSummary(story.summary))
            .put("coverUrl", safeUrl(story.coverUrl))
            .put("dimension", rating.dimension.key)
            .put("dimensionLabel", BriefingText.of(language).dimensions.getValue(rating.dimension))
            .put("scores", scores)
            .put("notes", notes)
            .put("impacts", impacts)
            .put("whyItMatters", rating.whyItMatters.orEmpty())
            .put("ratedBy", if (rating.ratedByAi) "ai" else "rules")
            .put("storyline", entry.storyline.orEmpty())
            .put("storylineWhy", entry.storylineWhy.orEmpty())
            .put("duplicateWhy", entry.duplicateWhy.orEmpty())
            .put(
                "resolution",
                entry.resolution?.let {
                    JSONObject().put("key", it.key).put("label", it.label).put("summary", it.summary).put("role", it.role.key).put("order", it.order)
                } ?: JSONObject.NULL,
            )
            .put("related", related)
    }

    /** Fills the page template (assets/briefing/page.html) with this briefing. */
    fun renderHtml(
        template: String,
        title: String,
        stories: List<RatedStory>,
        limit: Int,
        storyCount: Int,
        generatedAtMs: Long,
        aiRan: Boolean,
        digest: List<DigestBullet> = emptyList(),
        language: BriefingLanguage = BriefingLanguage.English,
        withDates: Boolean = false,
    ): String {
        val t = BriefingText.of(language)
        // A bullet may name a report that was folded under another story; it points to that story.
        val indexById = mutableMapOf<String, Int>()
        stories.forEachIndexed { index, entry ->
            (listOf(entry.story) + entry.related).forEach { other -> indexById.putIfAbsent(other.id, index) }
        }
        val digestJson = JSONArray()
        digest.forEach { bullet ->
            digestJson.put(
                JSONObject()
                    .put("kind", bullet.kind.key)
                    .put("text", bullet.text)
                    .put("targets", JSONArray(bullet.ids.mapNotNull { indexById[it] }.distinct())),
            )
        }
        val data = JSONObject()
            .put("stories", JSONArray().also { array -> stories.forEach { array.put(pageStory(it, language, withDates)) } })
            .put("digest", digestJson)
            .put("storyCount", storyCount)
            .put("limit", limit)
            .put("defaults", DEFAULT_WEIGHTS.toWeightsJson())
            .put("strength", SINGLE_CRITERION_STRENGTH)
            .put("aiRan", aiRan)
            .put("uncheckedCap", UNCHECKED_SCORE_CAP)
            .put("thresholds", JSONObject().put("critical", CRITICAL_AT).put("high", HIGH_AT).put("notable", NOTABLE_AT))
            .put(
                "presets",
                JSONArray().also { array ->
                    PRESETS.forEachIndexed { index, (name, weights) ->
                        array.put(JSONObject().put("name", t.presets.getOrElse(index) { name }).put("weights", weights.toWeightsJson()))
                    }
                },
            )
            .put("levelLabels", JSONObject().also { json -> PriorityLevel.entries.forEach { json.put(it.key, t.levels.getValue(it)) } })
            .put(
                "criteria",
                JSONArray().also { array ->
                    Criterion.entries.forEach {
                        array.put(JSONObject().put("key", it.key).put("label", t.criteria.getValue(it)).put("text", t.criteriaText.getValue(it)))
                    }
                },
            )
            .put(
                "angles",
                JSONArray().also { array -> ImpactAngle.entries.forEach { array.put(JSONObject().put("key", it.key).put("label", t.angles.getValue(it))) } },
            )
            .put("text", JSONObject(t.page))
            .put("language", language.key)
        val ratedByAi = stories.any { it.rating.ratedByAi }
        val legend = Criterion.entries.joinToString("") {
            "<li><strong>${escapeHtml(t.criteria.getValue(it))}</strong>: ${escapeHtml(t.criteriaText.getValue(it))}</li>"
        }
        val count = BriefingText.fill(t.page.getValue("of"), "a" to minOf(limit, stories.size), "b" to storyCount)
        val generated = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, language.locale).format(Date(generatedAtMs))
        return template
            .replace("{{LANG}}", language.key)
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{COUNT_LINE}}", t.countLine("<span id=\"count\">$count</span>", escapeHtml(generated)))
            .replace("{{FOOTER}}", t.footer(ratedByAi))
            .replace("{{LEGEND}}", legend)
            .replace("{{IMPORTANCE_ARIA}}", t.importanceAria)
            .replace("{{IMPORTANCE_HINT}}", t.importanceHint)
            .replace("{{IMPORTANCE}}", t.importance)
            .replace("{{CLOSE}}", t.close)
            .replace("{{GO_TOP}}", t.goTop)
            .replace("{{GO_BOTTOM}}", t.goBottom)
            .replace("{{DATA_JSON}}", scriptJson(data))
    }

    // ---- building ----

    private val random = SecureRandom()
    private val ID_PATTERN = Regex("^\\d+-[0-9a-f]{18}$")

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    private fun newId(startMs: Long): String {
        val bytes = ByteArray(9).also(random::nextBytes)
        return "$startMs-" + bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Rates, groups, summarises and renders a briefing. The AI steps are optional: when they
     * are missing or fail, keyword rules rate the stories, the title rule merges duplicates
     * and the page lists the top stories' own summaries.
     */
    suspend fun build(
        stories: List<BriefingStory>,
        options: BriefingOptions,
        template: String,
        rateWithAi: (suspend (List<AiRatingCandidate>) -> List<AiStoryRating>)? = null,
        groupWithAi: (suspend (List<GroupingItem>) -> StoryGrouping)? = null,
        digestWithAi: (suspend (List<DigestItem>) -> List<DigestBullet>)? = null,
    ): Briefing {
        val window = options.window
        val language = options.language
        // The page holds more than it shows (pageSize), so moving the sliders can bring other stories up.
        val (limit, pageSize) = briefingSize(window)
        val inWindow = stories.filter { it.publishedAt >= window.startMs && it.publishedAt < window.endMs }
        // A month holds thousands of stories. The keyword rules rank them one by one first and
        // only the strongest go on to merging and the AI, which compare stories with each other.
        val candidates = if (inWindow.size > PRE_RANK_LIMIT) {
            inWindow
                .map { it to scoreOf(ruleRating(it, emptyList(), window, options.trackedTerms, options.localTerms, options.region).scores) }
                .sortedWith(compareByDescending<Pair<BriefingStory, Int>> { it.second }.thenByDescending { it.first.publishedAt })
                .take(PRE_RANK_LIMIT)
                .map { it.first }
        } else {
            inWindow
        }
        // With the AI grouping the briefing, only the same link or title is merged up front; word
        // matches go to it as hints so it decides from what the stories say.
        var rated = clusterStories(candidates, loose = true, exactOnly = groupWithAi != null).map { (story, related) ->
            val rating = ruleRating(story, related, window, options.trackedTerms, options.localTerms, options.region, language)
            val score = scoreOf(rating.scores)
            RatedStory(story, related, rating, score, levelOf(score))
        }

        var aiRan = false
        if (rateWithAi != null && rated.isNotEmpty()) {
            // Every story that can reach the page goes to the AI (a few spare), so keyword
            // guesses never decide what leads.
            val candidates = rated.sortedByDescending { it.score }.take(pageSize + 10)
            fun candidate(entry: RatedStory) = AiRatingCandidate(
                id = entry.story.id,
                title = entry.story.title,
                summary = entry.story.summary.take(400),
                source = entry.story.source,
                alsoReportedBy = distinctSources(entry.related),
            )
            // Small batches in parallel: each answer stays short enough to finish, and one
            // failed batch only leaves its own stories on rule ratings.
            suspend fun rateInBatches(entries: List<RatedStory>): List<AiStoryRating> = coroutineScope {
                entries.chunked(10)
                    .map { batch -> async { runCatching { rateWithAi(batch.map(::candidate)) }.getOrDefault(emptyList()) } }
                    .awaitAll()
                    .flatten()
            }
            val byId = rateInBatches(candidates).associateBy { it.id }.toMutableMap()
            // Answers sometimes skip a story or get cut off; ask once more for the ones missing.
            val missing = candidates.filter { it.story.id !in byId }
            if (missing.isNotEmpty() && byId.isNotEmpty()) {
                rateInBatches(missing).forEach { byId[it.id] = it }
            }
            aiRan = byId.isNotEmpty()
            val candidateIds = candidates.map { it.story.id }.toSet()
            rated = rated.map { entry ->
                val ai = byId[entry.story.id]?.takeIf { entry.story.id in candidateIds }
                if (ai == null) {
                    // A story the AI never checked cannot claim Critical or High from keywords alone.
                    if (aiRan) {
                        val score = minOf(entry.score, UNCHECKED_SCORE_CAP)
                        entry.copy(score = score, level = levelOf(score))
                    } else {
                        entry
                    }
                } else {
                    val rating = applyAiRating(entry.rating, ai)
                    val score = scoreOf(rating.scores)
                    entry.copy(rating = rating, score = score, level = levelOf(score))
                }
            }
        }

        var merged = rated.sortedWith(byScore).take(pageSize)
        if (groupWithAi != null && merged.size >= 2) {
            val hints = possibleDuplicates(merged)
            val grouping = runCatching {
                groupWithAi(
                    merged.map { entry ->
                        val maybeSameAs = hints.firstOrNull { entry.story.id in it }?.filter { it != entry.story.id }.orEmpty()
                        GroupingItem(entry.story.id, entry.story.title, entry.story.summary.take(200), maybeSameAs)
                    },
                )
            }.getOrNull()
            // No AI answer: fall back to the title rule's guesses, without storylines.
            merged = applyGrouping(merged, grouping ?: StoryGrouping(duplicates = hints), aiRan, language)
        }

        val picked = pickVaried(merged, limit)
        val texts = BriefingText.of(language)
        val digest = if (digestWithAi == null || picked.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                digestWithAi(
                    picked.map { entry ->
                        DigestItem(
                            id = entry.story.id,
                            title = entry.story.title,
                            summary = entry.story.summary.take(300),
                            level = entry.level,
                            score = entry.score,
                            area = texts.dimensions.getValue(entry.rating.dimension),
                            storyline = entry.storyline.orEmpty(),
                            sources = distinctSources(listOf(entry.story) + entry.related).size,
                            impacts = entry.rating.impacts,
                            resolved = entry.resolution?.let { it.summary.ifBlank { it.label } },
                        )
                    },
                )
            }.getOrDefault(emptyList())
        }
        val title = briefingTitle(options.period, options.scope, window, language)
        val record = BriefingRecord(
            // Random part keeps the id unguessable, like the Mac backend's briefing URLs.
            id = newId(window.startMs),
            title = title,
            period = options.period,
            scope = options.scope,
            scheduled = false,
            windowStartMs = window.startMs,
            windowEndMs = window.endMs,
            generatedAtMs = options.generatedAtMs,
            itemCount = picked.size,
        )
        return Briefing(record, renderHtml(template, title, merged, limit, inWindow.size, options.generatedAtMs, aiRan, digest, language, spansDays(window)))
    }
}

// ---- reading a saved briefing back for the in-app screen ----

data class PageRelated(val title: String, val source: String, val time: String, val link: String)

/** One story as the saved page holds it. */
data class PageStory(
    /** Position on the page, which the summary's lines point at. */
    val index: Int,
    val title: String,
    val link: String,
    val source: String,
    val time: String,
    val publishedMs: Long,
    val summary: String,
    val coverUrl: String,
    val dimension: ImpactDimension,
    val dimensionLabel: String,
    val scores: Map<Criterion, Int>,
    val notes: Map<Criterion, String>,
    val impacts: Map<ImpactAngle, String>,
    val whyItMatters: String,
    val ratedByAi: Boolean,
    val storyline: String,
    val storylineWhy: String,
    val duplicateWhy: String,
    val related: List<PageRelated>,
    val resolution: StoryResolution? = null,
)

/** A line of the AI's summary: the stories it points at, by [PageStory.index]. */
data class PageDigestLine(val kind: DigestKind, val text: String, val targets: List<Int>)

data class BriefingPage(
    val title: String,
    val storyCount: Int,
    val limit: Int,
    val aiRan: Boolean,
    val stories: List<PageStory>,
    val digest: List<PageDigestLine> = emptyList(),
    /** The labels the page was written with, so the screen speaks its language. */
    val text: BriefingText = BriefingText.English,
    val pageText: Map<String, String> = BriefingText.English.page,
) {
    fun say(key: String, vararg values: Pair<String, Any>): String =
        BriefingText.fill(pageText[key] ?: text.page[key] ?: BriefingText.English.page[key].orEmpty(), *values)
}

data class RankedStory(val story: PageStory, val score: Int, val level: PriorityLevel)

/**
 * One story, a storyline (several different stories about one ongoing situation), or an
 * event that began and ended within the period, with the resolution first.
 */
data class BriefingUnit(val label: String, val entries: List<RankedStory>) {
    val lead: RankedStory get() = entries.first()
    val resolved: Boolean get() = entries.size > 1 && entries.first().story.resolution != null
    val resolutionSummary: String get() = entries.firstNotNullOfOrNull { it.story.resolution?.summary?.ifBlank { null } }.orEmpty()
    val why: String get() = entries.firstOrNull { it.story.storylineWhy.isNotBlank() }?.story?.storylineWhy.orEmpty()
}

data class RankedBriefing(val shown: List<BriefingUnit>, val more: List<BriefingUnit>)

/** A line of "At a glance": no level word, only the closing curiosity is [marked]. */
data class GlanceLine(val text: String, val level: PriorityLevel, val targets: List<Int>, val marked: Boolean)

object BriefingPageReader {
    val presets: List<Pair<String, Map<Criterion, Double>>> = listOf(
        "Balanced" to BriefingEngine.DEFAULT_WEIGHTS,
        "Critical first" to weights(5.0, 3.0, 3.0, 0.5, 2.0, 1.0),
        "Interesting" to weights(1.0, 1.0, 0.5, 5.0, 1.0, 2.0),
        "Close to home" to weights(2.0, 1.0, 1.5, 1.5, 1.0, 5.0),
    )

    private fun weights(vararg values: Double): Map<Criterion, Double> = Criterion.entries.zip(values.toList()).toMap()

    /** Reads the data the page carries for its own script, so old and new briefings both open. */
    fun parse(html: String): BriefingPage? {
        val json = html.substringAfter("<script id=\"briefing-data\" type=\"application/json\">", "")
            .substringBefore("</script>", "")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return null
        val title = html.substringAfter("<title>", "").substringBefore("</title>", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        val stories = json.optJSONArray("stories") ?: JSONArray()
        // Older pages carry no language; they were written in English.
        val text = BriefingText.of(BriefingLanguage.fromKey(json.optString("language")))
        val pageText = json.optJSONObject("text")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } }
            ?: text.page
        val digest = json.optJSONArray("digest") ?: JSONArray()
        return BriefingPage(
            title = title,
            storyCount = json.optInt("storyCount"),
            limit = json.optInt("limit", 12),
            aiRan = json.optBoolean("aiRan"),
            stories = (0 until stories.length()).mapNotNull { index -> stories.optJSONObject(index)?.let { pageStory(index, it, text) } },
            digest = (0 until digest.length()).mapNotNull { index ->
                val line = digest.optJSONObject(index) ?: return@mapNotNull null
                val targets = line.optJSONArray("targets")
                PageDigestLine(
                    kind = DigestKind.entries.firstOrNull { it.key == line.optString("kind") } ?: DigestKind.Leading,
                    text = line.optString("text"),
                    targets = if (targets == null) emptyList() else (0 until targets.length()).map { targets.optInt(it) },
                )
            },
            text = text,
            pageText = pageText,
        )
    }

    private fun pageStory(index: Int, json: JSONObject, text: BriefingText): PageStory {
        val scores = json.optJSONObject("scores")
        val notes = json.optJSONObject("notes")
        val impacts = json.optJSONObject("impacts")
        val related = json.optJSONArray("related") ?: JSONArray()
        val dimension = ImpactDimension.fromKey(json.optString("dimension"))
        return PageStory(
            index = index,
            title = json.optString("title"),
            link = json.optString("link"),
            source = json.optString("source"),
            time = json.optString("time"),
            publishedMs = json.optLong("publishedMs"),
            summary = SummaryText.cleanSummary(json.optString("summary")),
            coverUrl = json.optString("coverUrl"),
            dimension = dimension,
            dimensionLabel = json.optString("dimensionLabel").ifBlank { text.dimensions.getValue(dimension) },
            scores = Criterion.entries.associateWith { (scores?.optInt(it.key) ?: 0).coerceIn(0, 3) },
            notes = Criterion.entries.mapNotNull { key -> notes?.optString(key.key)?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap(),
            impacts = ImpactAngle.entries.mapNotNull { angle -> impacts?.optString(angle.key)?.takeIf { it.isNotBlank() }?.let { angle to it } }.toMap(),
            whyItMatters = json.optString("whyItMatters"),
            ratedByAi = json.optString("ratedBy") == "ai",
            storyline = json.optString("storyline"),
            storylineWhy = json.optString("storylineWhy"),
            duplicateWhy = json.optString("duplicateWhy"),
            resolution = json.optJSONObject("resolution")?.let { arc ->
                StoryResolution(
                    key = arc.optString("key"),
                    label = arc.optString("label"),
                    summary = arc.optString("summary"),
                    role = ResolutionRole.entries.firstOrNull { it.key == arc.optString("role") } ?: ResolutionRole.Update,
                    order = arc.optInt("order"),
                )
            },
            related = (0 until related.length()).mapNotNull { at ->
                related.optJSONObject(at)?.let {
                    PageRelated(it.optString("title"), it.optString("source"), it.optString("time"), it.optString("link"))
                }
            },
        )
    }

    /**
     * The summary at the top, as the page script builds it. The AI's lines when it wrote
     * them, otherwise every Critical and High story (up to five), topped up to three with
     * the next strongest, plus the most surprising one. No level words: the order already
     * says what leads, and the AI tends to call everything critical. Each line takes the
     * level of the strongest story it points to, at the reader's current weights.
     */
    fun glance(page: BriefingPage, ranked: RankedBriefing): List<GlanceLine> {
        // One line per resolved event: its strongest report stands for it, told by its summary.
        val seenArcs = mutableSetOf<String>()
        val entries = ranked.shown.flatMap { it.entries }
            .sortedWith(compareByDescending<RankedStory> { it.score }.thenByDescending { it.story.publishedMs })
            .filter { entry -> entry.story.resolution?.let { seenArcs.add(it.key) } ?: true }
        val levelAt = entries.associate { it.story.index to it.level }
        fun levelOf(targets: List<Int>): PriorityLevel =
            PriorityLevel.entries.firstOrNull { level -> targets.any { levelAt[it] == level } } ?: PriorityLevel.Routine
        if (page.digest.isNotEmpty()) {
            return page.digest.map { GlanceLine(it.text, levelOf(it.targets), it.targets, it.kind == DigestKind.Interesting) }
        }
        val lead = entries.filter { it.level == PriorityLevel.Critical || it.level == PriorityLevel.High }.take(5).toMutableList()
        for (entry in entries) {
            if (lead.size >= 3) break
            if (entry !in lead) lead.add(entry)
        }
        val curious = entries
            .filter { it !in lead && (it.story.scores[Criterion.Interest] ?: 0) >= 2 }
            .sortedWith(compareByDescending<RankedStory> { it.story.scores[Criterion.Interest] ?: 0 }.thenByDescending { it.score })
            .firstOrNull()
        fun line(entry: RankedStory, marked: Boolean) = GlanceLine(
            entry.story.resolution?.summary?.ifBlank { null } ?: entry.story.summary.ifBlank { entry.story.title },
            entry.level,
            listOf(entry.story.index),
            marked,
        )
        return lead.map { line(it, false) } + listOfNotNull(curious?.let { line(it, true) })
    }

    /** Each criterion's share of [score], in proportion to how hard it pushed the story up. */
    fun shares(scores: Map<Criterion, Int>, weights: Map<Criterion, Double>, score: Int): Map<Criterion, Int> {
        val heaviest = Criterion.entries.maxOf { (weights[it] ?: 0.0).coerceAtLeast(0.0) }
        val lifts = Criterion.entries.associateWith { key ->
            if (heaviest <= 0.0) 0.0
            else (weights[key] ?: 0.0).coerceAtLeast(0.0) / heaviest * (scores[key] ?: 0) / 3.0 * BriefingEngine.SINGLE_CRITERION_STRENGTH
        }
        val total = lifts.values.sum()
        return lifts.mapValues { (_, lift) -> if (total > 0) (score * lift / total).roundToInt() else 0 }
    }

    /**
     * Ranks the stories with the reader's weights, the way the page's own script does:
     * stories the AI never checked stay below High, storylines rank by their strongest
     * story, and no area takes more than about a third of what is shown.
     */
    fun rank(page: BriefingPage, weights: Map<Criterion, Double>): RankedBriefing {
        val ranked = page.stories.map { story ->
            val raw = BriefingEngine.scoreOf(story.scores, weights)
            val score = if (page.aiRan && !story.ratedByAi) minOf(raw, BriefingEngine.UNCHECKED_SCORE_CAP) else raw
            RankedStory(story, score, BriefingEngine.levelOf(score))
        }.sortedWith(compareByDescending<RankedStory> { it.score }.thenByDescending { it.story.publishedMs })

        // Plain classes, compared by identity: a unit's list grows while it is being built.
        class Builder(val label: String, val entries: MutableList<RankedStory>)
        val units = mutableListOf<Builder>()
        val byLine = mutableMapOf<String, Builder>()
        for (entry in ranked) {
            val arc = entry.story.resolution
            val key = arc?.key ?: entry.story.storyline.trim().lowercase(Locale.ROOT)
            val existing = if (key.isNotEmpty()) byLine[key] else null
            if (existing != null) {
                existing.entries.add(entry)
                continue
            }
            val unit = Builder(arc?.label ?: entry.story.storyline, mutableListOf(entry))
            if (key.isNotEmpty()) byLine[key] = unit
            units.add(unit)
        }
        // The resolution leads, then the updates from the latest back to how it began.
        units.filter { it.entries.first().story.resolution != null }.forEach { unit ->
            unit.entries.sortWith(
                compareBy<RankedStory> { it.story.resolution?.role?.ordinal ?: 0 }.thenByDescending { it.story.resolution?.order ?: 0 },
            )
        }

        val cap = maxOf(2, ceil(page.limit * 0.3).toInt())
        val picked = mutableListOf<Builder>()
        val overflow = mutableListOf<Builder>()
        val perDimension = mutableMapOf<ImpactDimension, Int>()
        for (unit in units) {
            if (picked.size >= page.limit) break
            val dimension = unit.entries.first().story.dimension
            if ((perDimension[dimension] ?: 0) >= cap) {
                overflow.add(unit)
                continue
            }
            perDimension[dimension] = (perDimension[dimension] ?: 0) + 1
            picked.add(unit)
        }
        while (picked.size < page.limit && overflow.isNotEmpty()) picked.add(overflow.removeAt(0))
        val pickedSet = picked.toHashSet()
        fun toUnit(unit: Builder) = BriefingUnit(if (unit.entries.size > 1) unit.label else "", unit.entries.toList())
        val byLead = compareByDescending<BriefingUnit> { it.lead.score }.thenByDescending { it.lead.story.publishedMs }
        return RankedBriefing(
            shown = picked.map(::toUnit).sortedWith(byLead),
            more = units.filter { it !in pickedSet }.map(::toUnit),
        )
    }
}
