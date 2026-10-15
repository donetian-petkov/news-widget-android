package com.ainews.android.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar

class BriefingEngineTest {
    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val hour = BriefingWindow(at(13), at(14))

    private fun story(
        id: String = "x",
        title: String = "Story",
        source: String = "Source",
        publishedAt: Long = at(13, 30),
        summary: String = "",
        coverUrl: String? = null,
        link: String? = null,
    ) = BriefingStory(id, title, link ?: "https://news.example/$id", source, publishedAt, summary, coverUrl)

    private fun scores(vararg pairs: Pair<Criterion, Int>): Map<Criterion, Int> =
        Criterion.entries.associateWith { 0 } + pairs

    private fun entry(id: String, score: Int, dimension: ImpactDimension) = RatedStory(
        story = story(id = id, title = id, source = id),
        related = emptyList(),
        rating = StoryRating(scores(), emptyMap(), dimension, ratedByAi = true),
        score = score,
        level = BriefingEngine.levelOf(score),
    )

    /** Only what the page needs: the data script, so tests can read what was rendered. */
    private val template = "<title>{{TITLE}}</title>{{COUNT}}|{{GENERATED}}|{{RATED_BY}}" +
        "<script id=\"briefing-data\" type=\"application/json\">{{DATA_JSON}}</script>"

    private fun pageData(html: String): JSONObject =
        JSONObject(html.substringAfter("application/json\">").substringBefore("</script>"))

    @Test
    fun previousHourIsTheLastFullClockHour() {
        assertEquals(hour, BriefingEngine.previousHourWindow(at(14, 5)))
    }

    @Test
    fun ratesADeadlyDisasterAboveASportsStoryAndQuotesTheWords() {
        val disaster = BriefingEngine.ruleRating(
            story(title = "School roof collapses in northwestern Pakistan, killing at least 29 people", source = "Al Jazeera"),
            emptyList(),
            hour,
        )
        val sports = BriefingEngine.ruleRating(
            story(title = "\"Това е война\": Каналът на Реал Мадрид с жестока атака преди Ел Класико"),
            emptyList(),
            hour,
        )
        assertEquals(ImpactDimension.Sports, sports.dimension)
        assertTrue(BriefingEngine.scoreOf(disaster.scores) > BriefingEngine.scoreOf(sports.scores))
        assertTrue(disaster.notes.getValue(Criterion.Severity).contains("killing at least 29"))
        assertEquals("Only Al Jazeera reports it", disaster.notes[Criterion.Coverage])
    }

    @Test
    fun namesTheSourcesTopicAndRegionThatMatched() {
        val rating = BriefingEngine.ruleRating(
            story(title = "България и Украйна обсъждат ударите по руски рафинерии", source = "A"),
            listOf(story(source = "B"), story(source = "C")),
            hour,
            trackedTerms = listOf("украйна", "AI"),
            localTerms = listOf("българ"),
            region = "Bulgaria",
        )
        assertEquals(2, rating.scores[Criterion.Coverage])
        assertEquals(2, rating.scores[Criterion.Relevance])
        assertEquals("Reported by A, B, C", rating.notes[Criterion.Coverage])
        assertEquals("Mentions your topic “украйна” and Bulgaria “България и Украйна…”", rating.notes[Criterion.Relevance])
    }

    @Test
    fun seesReachInStoriesThatInvolveSeveralCountries() {
        val rating = BriefingEngine.ruleRating(
            story(title = "Зеленски: Почваме пак да удряме руските рафинерии", summary = "Украйна ще продължи да удря руските рафинерии."),
            emptyList(),
            hour,
        )
        assertEquals(2, rating.scores[Criterion.Scale])
        assertTrue(rating.notes.getValue(Criterion.Scale).contains("several countries"))
    }

    @Test
    fun zeroScoresCarryAReasonToo() {
        val rating = BriefingEngine.ruleRating(story(title = "Local council meets"), emptyList(), hour)
        assertEquals("No deaths, injuries, damage or money lost are mentioned", rating.notes[Criterion.Severity])
    }

    @Test
    fun oneStrongCriterionCarriesAStoryAndMoreLiftItFurther() {
        assertEquals(65, BriefingEngine.scoreOf(scores(Criterion.Severity to 3)))
        assertEquals(PriorityLevel.High, BriefingEngine.levelOf(65))
        assertTrue(BriefingEngine.scoreOf(scores(Criterion.Severity to 3, Criterion.Scale to 3)) > 80)
        assertEquals(0, BriefingEngine.scoreOf(scores()))
        val onlyInterest = Criterion.entries.associateWith { if (it == Criterion.Interest) 5.0 else 0.0 }
        assertEquals(65, BriefingEngine.scoreOf(scores(Criterion.Interest to 3), onlyInterest))
    }

    @Test
    fun clustersTheSameEventFromSeveralSources() {
        val clusters = BriefingEngine.clusterStories(
            listOf(
                story(id = "nyt", title = "Zelensky Says Ukraine Will Resume Strikes on Russian Oil Refineries", publishedAt = at(13, 5)),
                story(id = "aj", title = "Ukraine to resume strikes on Russian oil refineries, Zelensky says", summary = "Detail", publishedAt = at(13, 20)),
                story(id = "other", title = "Messi’s Inter Miami reach playoff", publishedAt = at(13, 10)),
            ),
        )
        assertEquals(2, clusters.size)
        assertEquals(listOf("nyt"), clusters.first { it.first.id == "aj" }.second.map { it.id })
    }

    @Test
    fun stopsOneAreaFromTakingOver() {
        val picked = BriefingEngine.pickVaried(
            listOf(
                entry("s1", 90, ImpactDimension.Security),
                entry("s3", 80, ImpactDimension.Security),
                entry("s4", 75, ImpactDimension.Security),
                entry("e1", 40, ImpactDimension.Economy),
                entry("h1", 30, ImpactDimension.Health),
            ),
            4,
        )
        assertEquals(listOf("s1", "s3", "e1", "h1"), picked.map { it.story.id })
    }

    @Test
    fun foldsDuplicatesAndGroupsStorylinesOfDifferentEvents() {
        val grouping = BriefingEngine.parseGrouping(
            """{"duplicates":[["nyt","bg"],["ghost","oman"],["a","b","c","d","e","f","g"]],
               "storylines":[{"label":"Black Sea security","ids":["drone","command"]},{"label":"Gulf ties","ids":["oman"]}]}""",
        )
        val grouped = BriefingEngine.applyGrouping(
            listOf(
                entry("bg", 80, ImpactDimension.Security),
                entry("nyt", 70, ImpactDimension.Security),
                entry("drone", 60, ImpactDimension.Security),
                entry("command", 40, ImpactDimension.Politics),
                entry("oman", 30, ImpactDimension.Politics),
            ),
            grouping,
        )
        assertEquals(listOf("bg", "drone", "command", "oman"), grouped.map { it.story.id })
        assertEquals(listOf("nyt"), grouped.first().related.map { it.id })
        assertEquals(listOf(null, "Black Sea security", "Black Sea security", null), grouped.map { it.storyline })
        assertEquals(2, grouping.duplicates.size)
    }

    @Test
    fun readsAiRatingsAroundExtraTextAndClampsThem() {
        val ratings = BriefingEngine.parseAiRatings(
            """Here: {"ratings":[{"id":"a","severity":7,"severityWhy":"29 killed","scale":2,"urgency":-1,"interest":1,
               "dimension":"nope","impacts":{"economic":"Fuel prices","political":"","weird":"x"}}]}""",
        )
        val rating = ratings.single()
        assertEquals(3, rating.scores[Criterion.Severity])
        assertEquals(0, rating.scores[Criterion.Urgency])
        assertEquals("29 killed", rating.whys[Criterion.Severity])
        assertEquals(ImpactDimension.Other, rating.dimension)
        assertEquals(mapOf(ImpactAngle.Economic to "Fuel prices"), rating.impacts)
        assertTrue(BriefingEngine.parseAiRatings("not json").isEmpty())
    }

    @Test
    fun asksAgainForSkippedStoriesAndKeepsUncheckedOnesBelowHigh() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val briefing = BriefingEngine.build(
            stories = listOf(
                story(id = "checked", title = "Quiet council meeting"),
                story(id = "skipped", title = "Невиждан срив и загуби, жертви и криза в цялата страна"),
            ),
            options = BriefingOptions(BriefingPeriod.Hour, "All", hour),
            template = template,
            rateWithAi = { candidates ->
                synchronized(calls) { calls.add(candidates.map { it.id }) }
                candidates.filter { it.id == "checked" }.map {
                    AiStoryRating(it.id, scores(), emptyMap(), ImpactDimension.Politics, mapOf(ImpactAngle.Economic to "none"))
                }
            },
        )
        assertEquals(listOf(listOf("skipped", "checked"), listOf("skipped")), calls)
        val data = pageData(briefing.html)
        assertTrue(data.getBoolean("aiRan"))
        val stories = data.getJSONArray("stories")
        val skipped = (0 until stories.length()).map { stories.getJSONObject(it) }.first { it.getString("title").startsWith("Невиждан") }
        assertEquals("rules", skipped.getString("ratedBy"))
        assertTrue(data.getInt("uncheckedCap") < BriefingEngine.HIGH_AT)
    }

    @Test
    fun keepsStoryTextOutOfTheMarkup() = runBlocking {
        val briefing = BriefingEngine.build(
            stories = listOf(story(title = "</script><script>alert(1)</script>", link = "javascript:alert(1)", coverUrl = "javascript:x")),
            options = BriefingOptions(BriefingPeriod.Day, "All", hour),
            template = template,
        )
        assertFalse(briefing.html.contains("<script>alert(1)"))
        assertFalse(briefing.html.contains("javascript:"))
        val first = pageData(briefing.html).getJSONArray("stories").getJSONObject(0)
        assertEquals("</script><script>alert(1)</script>", first.getString("title"))
        assertEquals("", first.getString("link"))
        assertTrue(briefing.record.title.startsWith("Daily briefing · All"))
    }

    @Test
    fun fillsEveryPlaceholderOfTheRealPageTemplate() = runBlocking {
        val template = File("src/main/assets/briefing/page.html").readText()
        val briefing = BriefingEngine.build(
            stories = listOf(story(title = "Drone strike in the Black Sea", summary = "Romania sent a rescue ship.")),
            options = BriefingOptions(BriefingPeriod.Hour, "All Feeds", hour),
            template = template,
        )
        assertFalse(briefing.html.contains("{{"))
        assertTrue(briefing.html.contains("id=\"eq-bands\""))
        assertEquals(1, pageData(briefing.html).getJSONArray("stories").length())
    }

    @Test
    fun readsASavedPageBackAndRanksItWithTheReadersWeights() = runBlocking {
        val briefing = BriefingEngine.build(
            stories = listOf(
                story(id = "drone", title = "Drone strike in the Black Sea"),
                story(id = "command", title = "Romania offers joint naval command"),
                story(id = "fish", title = "Scientists discover a record deep sea fish"),
            ),
            options = BriefingOptions(BriefingPeriod.Hour, "All & more", hour),
            template = template,
            rateWithAi = { candidates ->
                candidates.map {
                    val drone = it.id == "drone"
                    AiStoryRating(
                        it.id,
                        scores(Criterion.Severity to if (drone) 3 else 1, Criterion.Interest to if (it.id == "fish") 3 else 0),
                        mapOf(Criterion.Severity to "fact ${it.id}"),
                        if (it.id == "fish") ImpactDimension.Environment else ImpactDimension.Security,
                        mapOf(ImpactAngle.Economic to "money ${it.id}"),
                    )
                }
            },
            groupWithAi = { StoryGrouping(storylines = mapOf("drone" to "Black Sea", "command" to "Black Sea")) },
        )
        val page = BriefingPageReader.parse(briefing.html)!!
        assertTrue(page.title.startsWith("Hourly briefing · All & more"))
        assertTrue(page.aiRan)
        assertEquals("fact drone", page.stories.first { it.title.startsWith("Drone") }.notes[Criterion.Severity])

        val balanced = BriefingPageReader.rank(page, BriefingEngine.DEFAULT_WEIGHTS)
        assertEquals("Black Sea", balanced.shown.first().label)
        assertEquals(2, balanced.shown.first().entries.size)

        val interesting = BriefingPageReader.rank(page, BriefingPageReader.presets.first { it.first == "Interesting" }.second)
        assertEquals("Scientists discover a record deep sea fish", interesting.shown.first().lead.story.title)

        val lead = balanced.shown.first().lead
        val shares = BriefingPageReader.shares(lead.story.scores, BriefingEngine.DEFAULT_WEIGHTS, lead.score)
        assertTrue(kotlin.math.abs(shares.values.sum() - lead.score) <= 1)
        assertNull(BriefingPageReader.parse("<html>no data</html>"))
    }

    @Test
    fun storeSavesReloadsReplacesAScheduledHourAndPrunes() = runBlocking {
        val dir = Files.createTempDirectory("briefings").toFile()
        val store = BriefingStore(dir, keep = 2)
        suspend fun make(h: Int, scheduled: Boolean, generatedAt: Long) = BriefingEngine.build(
            emptyList(),
            BriefingOptions(BriefingPeriod.Hour, "All", BriefingWindow(at(h), at(h + 1)), scheduled = scheduled, generatedAtMs = generatedAt),
            template,
        )
        val first = make(10, true, 1)
        store.save(first)
        store.save(make(11, true, 2))
        val redo = make(11, true, 3)
        store.save(redo)
        store.save(make(11, false, 4))

        val reloaded = BriefingStore(dir, keep = 2)
        assertEquals(listOf(at(11) to false, at(11) to true), reloaded.list().map { it.windowStartMs to it.scheduled })
        assertTrue(reloaded.hasScheduledWindow(at(11)))
        assertTrue(reloaded.readHtml(redo.record.id)!!.contains("briefing-data"))
        assertNull(reloaded.readHtml(first.record.id))
        assertNull(reloaded.readHtml("../index"))
        assertEquals(2, dir.listFiles { file: File -> file.name.endsWith(".html") }!!.size)
    }
}
