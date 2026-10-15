package com.ainews.android.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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

enum class BriefingPeriod(val key: String) {
    Hour("hour"),
    Day("day"),
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
)

data class GroupingItem(val id: String, val title: String, val summary: String)

data class StoryGrouping(
    /** Sets of ids that report the very same happening. */
    val duplicates: List<List<String>> = emptyList(),
    /** id to the label of the larger ongoing situation it belongs to. */
    val storylines: Map<String, String> = emptyMap(),
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
                period = if (json.optString("period") == BriefingPeriod.Day.key) BriefingPeriod.Day else BriefingPeriod.Hour,
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
    val scheduled: Boolean = false,
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
        val heaviest = Criterion.entries.maxOf { (weights[it] ?: 0.0).coerceAtLeast(0.0) }
        if (heaviest <= 0.0) return 0
        val unlifted = Criterion.entries.fold(1.0) { rest, key ->
            val lift = ((weights[key] ?: 0.0).coerceAtLeast(0.0) / heaviest) *
                (clamp(scores[key] ?: 0) / 3.0) * SINGLE_CRITERION_STRENGTH
            rest * (1 - lift)
        }
        return ((1 - unlifted) * 100).roundToInt()
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
    ): StoryRating {
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
            notes[Criterion.Severity] = "${dimension.label} story, but no harm is reported yet"
        }
        // Sports and entertainment borrow dramatic words ("war", "attack") without real-world harm.
        if (lightweight && severity > 1) {
            severity = 1
            notes[Criterion.Severity] = "${quoteAround(story, severityHits.first())} — figure of speech in a " +
                "${dimension.label.lowercase(Locale.ROOT)} story"
        }

        val scaleHits = termHits(textWords, SCALE_TERMS)
        val countryHits = termHits(textWords, COUNTRY_TERMS)
        val reach = (scaleHits.size + if (countryHits.size >= 2) 2 else countryHits.size).coerceAtMost(3)
        val scale = if (lightweight) reach.coerceAtMost(1) else reach
        if (scale > 0) {
            notes[Criterion.Scale] = if (countryHits.size >= 2) {
                "Involves several countries: " + countryHits.take(3).joinToString(", ") { "“$it”" }
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
        if (severity == 0) notes[Criterion.Severity] = "No deaths, injuries, damage or money lost are mentioned"
        if (scale == 0) notes[Criterion.Scale] = "No country-wide or international reach is mentioned"
        if (urgency == 0) notes[Criterion.Urgency] = "No deadline, live event or warning is mentioned"
        if (interest == 0) notes[Criterion.Interest] = "No first, record, surprise or new revelation in the headline"

        val sources = distinctSources(listOf(story) + related).map(::shortSource)
        val coverage = coverageScore(sources.size)
        notes[Criterion.Coverage] = if (sources.size > 1) {
            "Reported by ${sources.joinToString(", ")}"
        } else {
            "Only ${sources.firstOrNull() ?: "one source"} reports it"
        }

        val topics = trackedHits(textWords, trackedTerms)
        val localHits = termHits(textWords, localTerms)
        val relevance = ((if (topics.isNotEmpty()) 1 else 0) + (if (topics.size > 1) 1 else 0) +
            (if (localHits.isNotEmpty()) 1 else 0)).coerceAtMost(3)
        val relevanceParts = buildList {
            if (topics.isNotEmpty()) {
                add("your ${if (topics.size > 1) "topics" else "topic"} " + topics.take(3).joinToString(", ") { "“$it”" })
            }
            if (localHits.isNotEmpty()) add("${region.ifBlank { "your region" }} ${quoteAround(story, localHits.first(), 2)}")
        }
        if (relevanceParts.isNotEmpty()) {
            notes[Criterion.Relevance] = "Mentions ${relevanceParts.joinToString(" and ")}"
        } else if (trackedTerms.isNotEmpty() || region.isNotBlank()) {
            notes[Criterion.Relevance] = "None of your ${trackedTerms.size} topics" +
                (if (region.isNotBlank()) " or $region" else "") + " is mentioned"
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

    private fun stemSet(text: String): Set<String> =
        words(text).filter { it.length >= 4 }.map { it.take(5) }.toSet()

    /** Groups stories whose titles share most of their words; the most informative one leads. */
    fun clusterStories(stories: List<BriefingStory>): List<Pair<BriefingStory, List<BriefingStory>>> {
        val clusters = mutableListOf<Pair<MutableList<BriefingStory>, MutableSet<String>>>()
        val seenLinks = mutableSetOf<String>()
        for (story in stories.sortedBy { it.publishedAt }) {
            val key = (story.link?.takeIf { it.isNotBlank() } ?: story.title).trim().lowercase(Locale.ROOT)
            if (key.isEmpty() || !seenLinks.add(key)) continue
            val stems = stemSet(story.title)
            val match = clusters.firstOrNull { (_, clusterStems) ->
                val shared = stems.count { it in clusterStems }
                val union = (stems + clusterStems).size.coerceAtLeast(1)
                shared >= 2 && shared.toDouble() / union >= 0.35
            }
            if (match != null) {
                match.first.add(story)
                match.second.addAll(stems)
            } else {
                clusters.add(mutableListOf(story) to stems.toMutableSet())
            }
        }
        fun informative(story: BriefingStory) = (if (story.summary.isNotBlank()) 2 else 0) + (if (story.coverUrl != null) 1 else 0)
        return clusters.map { (members, _) ->
            val lead = members.sortedWith(compareByDescending<BriefingStory> { informative(it) }.thenBy { it.publishedAt }).first()
            lead to members.filter { it !== lead }
        }
    }

    // ---- AI rating ----

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
            "Score four criteria from 0 (none) to 3 (strong):",
            "- severity: ${Criterion.Severity.text}. Sports, celebrity and entertainment score 0-1 even with dramatic words like \"war\" or \"attack\".",
            "- scale: 1 one town or group, 2 a whole country, 3 several countries or the world.",
            "- urgency: 3 happening now or a deadline within days, 2 this week, 1 slow-moving, 0 none.",
            "- interest: how surprising, new or revealing it is (a first, a record, an unexpected twist, a hidden fact). Routine statements and scheduled events score 0-1.",
            "For each criterion add \"<criterion>Why\": at most 15 words naming the fact, e.g. \"29 pupils killed when a school roof fell in Khyber Pakhtunkhwa\".",
            "Fill it for low scores too, saying what this story lacks, e.g. severityWhy \"A TV casting change; no one is harmed and no money is at stake\".",
            "Add \"impacts\" with one specific sentence (at most 25 words) per angle about THIS story:",
            "- economic: prices, jobs, markets, budgets, trade, energy, investment. Always fill it; if the effect is indirect, explain the chain (e.g. strikes on refineries -> fuel supply -> pump prices).",
            "- political: governments, laws, elections, diplomacy, institutions.",
            "- social: daily life, health, education, rights, communities.",
            "- security: physical safety, conflict, crime, infrastructure.",
            "Fill at least two angles for every story, the economic one and the next most affected; use \"\" only where there is truly no effect.",
            "Pick one dimension from: ${ImpactDimension.entries.joinToString(", ") { it.key }}.",
            "Write every text field in ${if (bulgarian) "Bulgarian" else "English"}.",
            "Return strict JSON only: {\"ratings\":[{\"id\":\"...\",\"severity\":0,\"severityWhy\":\"...\",\"scale\":0,\"scaleWhy\":\"...\",\"urgency\":0,\"urgencyWhy\":\"...\",\"interest\":0,\"interestWhy\":\"...\",\"dimension\":\"economy\",\"impacts\":{\"economic\":\"...\",\"political\":\"\",\"social\":\"\",\"security\":\"\"}}]}",
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
     * by accident and glue unrelated stories together.
     */
    fun buildGroupingPrompt(items: List<GroupingItem>, bulgarian: Boolean): String {
        val json = JSONArray()
        items.forEach { json.put(JSONObject().put("id", it.id).put("title", it.title).put("summary", it.summary)) }
        return listOf(
            "You are given news items (id, headline, summary). Do two things.",
            "1. \"duplicates\": lists of ids that report the SAME happening: the same actors doing the same thing at the same",
            "   place and time, possibly worded differently or in another language. Two different strikes, two different",
            "   statements, or two events in the same war are NOT duplicates. When unsure, do not list them. Most items have no duplicate.",
            "2. \"storylines\": larger ongoing situations that at least two DIFFERENT happenings belong to, for example a drone",
            "   strike on a ship, a naval command offer and a moratorium proposal that are all about Black Sea security.",
            "   Give each a 2-5 word label in ${if (bulgarian) "Bulgarian" else "English"} and the ids that belong to it. An item can be in at most one storyline.",
            "Return strict JSON only: {\"duplicates\":[[\"id\",\"id\"]],\"storylines\":[{\"label\":\"...\",\"ids\":[\"id\",\"id\"]}]}",
            "Items: $json",
        ).joinToString("\n")
    }

    fun parseGrouping(text: String): StoryGrouping {
        val json = jsonObjectIn(text) ?: return StoryGrouping()
        fun ids(array: JSONArray?): List<String> =
            if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf { id -> id.isNotEmpty() } }
        val duplicates = json.optJSONArray("duplicates")?.let { sets ->
            (0 until sets.length()).mapNotNull { index ->
                // A long list is the model lumping a whole topic together, not one happening.
                ids(sets.optJSONArray(index)).distinct().takeIf { it.size in 2..6 }
            }
        }.orEmpty()
        val storylines = mutableMapOf<String, String>()
        json.optJSONArray("storylines")?.let { lines ->
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                val label = line.optString("label").trim().take(60)
                if (label.isEmpty()) continue
                ids(line.optJSONArray("ids")).forEach { id -> storylines.putIfAbsent(id, label) }
            }
        }
        return StoryGrouping(duplicates, storylines)
    }

    private fun labelKey(label: String): String = words(label).joinToString(" ")

    /**
     * Folds each set of duplicate reports into its strongest story (the others become the
     * "more reports" rows), then marks storylines that at least two remaining stories share.
     */
    fun applyGrouping(rated: List<RatedStory>, grouping: StoryGrouping): List<RatedStory> {
        val byId = rated.associateBy { it.story.id }
        val absorbed = mutableSetOf<String>()
        val extraRelated = mutableMapOf<String, List<BriefingStory>>()
        for (set in grouping.duplicates) {
            val members = set.mapNotNull { byId[it] }.filter { it.story.id !in absorbed }
            if (members.size < 2) continue
            val sorted = members.sortedWith(compareByDescending<RatedStory> { it.score }.thenByDescending { it.story.publishedAt })
            val lead = sorted.first()
            for (other in sorted.drop(1)) {
                absorbed.add(other.story.id)
                extraRelated[lead.story.id] = extraRelated[lead.story.id].orEmpty() + other.story + other.related
            }
        }
        val kept = rated
            .filter { it.story.id !in absorbed }
            .map { entry -> extraRelated[entry.story.id]?.let { entry.copy(related = entry.related + it) } ?: entry }
        val counts = kept.mapNotNull { grouping.storylines[it.story.id]?.let(::labelKey)?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
        return kept.map { entry ->
            val label = grouping.storylines[entry.story.id].orEmpty()
            if ((counts[labelKey(label)] ?: 0) >= 2) entry.copy(storyline = label) else entry
        }
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

    /** The last full clock hour before [nowMs], e.g. 13:00-14:00 when it is 14:05. */
    fun previousHourWindow(nowMs: Long): BriefingWindow {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endMs = calendar.timeInMillis
        return BriefingWindow(endMs - HOUR_MS, endMs)
    }

    /** The hour or day that ends now. */
    fun rollingWindow(period: BriefingPeriod, nowMs: Long): BriefingWindow =
        BriefingWindow(nowMs - if (period == BriefingPeriod.Day) 24 * HOUR_MS else HOUR_MS, nowMs)

    private const val HOUR_MS = 60L * 60 * 1000

    private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    private fun formatDay(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

    fun briefingTitle(period: BriefingPeriod, scope: String, window: BriefingWindow): String {
        val whenText = if (period == BriefingPeriod.Day) {
            "${formatDay(window.startMs)} ${formatTime(window.startMs)} – ${formatDay(window.endMs)} ${formatTime(window.endMs)}"
        } else {
            "${formatDay(window.startMs)}, ${formatTime(window.startMs)}–${formatTime(window.endMs)}"
        }
        return "${if (period == BriefingPeriod.Day) "Daily" else "Hourly"} briefing · $scope · $whenText"
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

    private fun pageStory(entry: RatedStory): JSONObject {
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
                    .put("time", formatTime(other.publishedAt))
                    .put("link", safeUrl(other.link)),
            )
        }
        return JSONObject()
            .put("title", story.title)
            .put("link", safeUrl(story.link))
            .put("source", shortSource(story.source))
            .put("time", formatTime(story.publishedAt))
            .put("publishedMs", story.publishedAt)
            .put("summary", story.summary)
            .put("coverUrl", safeUrl(story.coverUrl))
            .put("dimension", rating.dimension.key)
            .put("dimensionLabel", rating.dimension.label)
            .put("scores", scores)
            .put("notes", notes)
            .put("impacts", impacts)
            .put("whyItMatters", rating.whyItMatters.orEmpty())
            .put("ratedBy", if (rating.ratedByAi) "ai" else "rules")
            .put("storyline", entry.storyline.orEmpty())
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
    ): String {
        val data = JSONObject()
            .put("stories", JSONArray().also { array -> stories.forEach { array.put(pageStory(it)) } })
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
                    PRESETS.forEach { (name, weights) -> array.put(JSONObject().put("name", name).put("weights", weights.toWeightsJson())) }
                },
            )
            .put("levelLabels", JSONObject().also { json -> PriorityLevel.entries.forEach { json.put(it.key, it.label) } })
            .put(
                "criteria",
                JSONArray().also { array ->
                    Criterion.entries.forEach { array.put(JSONObject().put("key", it.key).put("label", it.label).put("text", it.text)) }
                },
            )
            .put(
                "angles",
                JSONArray().also { array -> ImpactAngle.entries.forEach { array.put(JSONObject().put("key", it.key).put("label", it.label)) } },
            )
        val ratedBy = if (stories.any { it.rating.ratedByAi }) {
            "Severity, scale, urgency, interest and the economic, political, social and security impact are judged by AI; " +
                "coverage and relevance come from your sources and topics."
        } else {
            "AI was off, so keyword rules did the rating and there is no impact write-up; turn AI on for both."
        }
        return template
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{COUNT}}", "${minOf(limit, stories.size)} of $storyCount")
            .replace("{{GENERATED}}", escapeHtml(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(generatedAtMs))))
            .replace("{{RATED_BY}}", escapeHtml(ratedBy))
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
     * Rates, groups and renders a briefing. [rateWithAi] and [groupWithAi] are optional;
     * when they are missing or fail, keyword rules rate the stories and nothing is grouped.
     */
    suspend fun build(
        stories: List<BriefingStory>,
        options: BriefingOptions,
        template: String,
        rateWithAi: (suspend (List<AiRatingCandidate>) -> List<AiStoryRating>)? = null,
        groupWithAi: (suspend (List<GroupingItem>) -> StoryGrouping)? = null,
    ): Briefing {
        val window = options.window
        val limit = if (options.period == BriefingPeriod.Day) 20 else 12
        // The page holds more than it shows, so moving the sliders can bring other stories up.
        val pageSize = if (options.period == BriefingPeriod.Day) 60 else 36
        val inWindow = stories.filter { it.publishedAt >= window.startMs && it.publishedAt < window.endMs }
        var rated = clusterStories(inWindow).map { (story, related) ->
            val rating = ruleRating(story, related, window, options.trackedTerms, options.localTerms, options.region)
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
            runCatching {
                groupWithAi(merged.map { GroupingItem(it.story.id, it.story.title, it.story.summary.take(160)) })
            }.getOrNull()?.let { grouping -> merged = applyGrouping(merged, grouping) }
        }

        val picked = pickVaried(merged, limit)
        val title = briefingTitle(options.period, options.scope, window)
        val record = BriefingRecord(
            // Random part keeps the id unguessable, like the Mac backend's briefing URLs.
            id = newId(window.startMs),
            title = title,
            period = options.period,
            scope = options.scope,
            scheduled = options.scheduled,
            windowStartMs = window.startMs,
            windowEndMs = window.endMs,
            generatedAtMs = options.generatedAtMs,
            itemCount = picked.size,
        )
        return Briefing(record, renderHtml(template, title, merged, limit, inWindow.size, options.generatedAtMs, aiRan))
    }
}
