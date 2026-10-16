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
    private val template = "<html lang=\"{{LANG}}\"><title>{{TITLE}}</title>{{COUNT_LINE}}|{{FOOTER}}" +
        "<script id=\"briefing-data\" type=\"application/json\">{{DATA_JSON}}</script>"

    private fun pageData(html: String): JSONObject =
        JSONObject(html.substringAfter("application/json\">").substringBefore("</script>"))

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
        assertTrue(BriefingEngine.scoreOf(scores(Criterion.Severity to 3, Criterion.Scale to 3, Criterion.Urgency to 3)) > 80)
        // Without anything happening now it stays just below Critical.
        assertEquals(69, BriefingEngine.scoreOf(scores(Criterion.Severity to 3, Criterion.Scale to 3)))
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
    fun storeSavesReloadsAndPrunes() = runBlocking {
        val dir = Files.createTempDirectory("briefings").toFile()
        val store = BriefingStore(dir, keep = 2)
        suspend fun make(h: Int, generatedAt: Long) = BriefingEngine.build(
            emptyList(),
            BriefingOptions(BriefingPeriod.Hour, "All", BriefingWindow(at(h), at(h + 1)), generatedAtMs = generatedAt),
            template,
        )
        val first = make(10, 1)
        store.save(first)
        val second = make(11, 2)
        store.save(second)
        val third = make(12, 3)
        store.save(third)

        val reloaded = BriefingStore(dir, keep = 2)
        assertEquals(listOf(at(12), at(11)), reloaded.list().map { it.windowStartMs })
        assertTrue(reloaded.readHtml(third.record.id)!!.contains("briefing-data"))
        assertNull(reloaded.readHtml(first.record.id))
        assertNull(reloaded.readHtml("../index"))
        assertEquals(2, dir.listFiles { file: File -> file.name.endsWith(".html") }!!.size)
    }

    @Test
    fun mergesReportsThatSpellNamesInAnotherScriptOrQuoteOtherLinesOfOneStatement() {
        val clusters = BriefingEngine.clusterStories(
            listOf(
                story(id = "tesla-a", title = "Първата летяща кола е тук: Tesla представи Roadster (ВИДЕО)"),
                story(id = "tesla-b", title = "\"Тесла\" демонстрира летяща версия на модела си \"Роудстър\""),
                story(id = "pm-a", title = "Костов, Борисов, Петков и Денков призоваха Радев да защити националната сигурност"),
                story(id = "pm-b", title = "Костов, Борисов и Петков: Мирът не се гарантира с надеждата, че Путин ще ни пощади"),
                story(id = "other", title = "Радев: Борисов носи отговорност за бюджета"),
                story(id = "sport", title = "Стоичков избухна след драмата на ЦСКА. Посочи виновника"),
            ),
        )
        val groups = clusters.map { (lead, related) -> (listOf(lead.id) + related.map { it.id }).sorted() }
        assertEquals(4, groups.size)
        assertTrue(listOf("tesla-a", "tesla-b") in groups)
        assertTrue(listOf("pm-a", "pm-b") in groups)
        assertTrue(listOf("sport") in groups)
        // Without the loose names rule only the Tesla pair (most words shared) merges.
        assertEquals(5, BriefingEngine.clusterStories(clusters.flatMap { listOf(it.first) + it.second }, loose = false).size)
    }

    @Test
    fun keepsASportsStoryOutOfAStorylineAboutSomethingElse() {
        val grouping = BriefingEngine.parseGrouping(
            """{"storylines":[{"label":"Ukraine war","why":"Both are about the war","ids":["starlink","ships","football"]},
               {"label":"CSKA","ids":["cska","coach"]}]}""",
        )
        val grouped = BriefingEngine.applyGrouping(
            listOf(
                entry("starlink", 60, ImpactDimension.Politics),
                entry("ships", 46, ImpactDimension.Society),
                entry("football", 43, ImpactDimension.Sports),
                entry("cska", 30, ImpactDimension.Sports),
                entry("coach", 28, ImpactDimension.Sports),
            ),
            grouping,
        )
        assertEquals(listOf("Ukraine war", "Ukraine war", null, "CSKA", "CSKA"), grouped.map { it.storyline })
        assertEquals("Both are about the war", grouped.first().storylineWhy)
    }

    @Test
    fun letsTheAiDecideOnNameOnlyMatchesShowsItsReasonAndCountsTheMergedSources() = runBlocking {
        val seen = mutableListOf<GroupingItem>()
        val stories = listOf(
            story(id = "pm-a", title = "Костов, Борисов, Петков и Денков призоваха Радев да защити националната сигурност", source = "Свободна точка"),
            story(id = "pm-b", title = "Костов, Борисов и Петков: Мирът не се гарантира с надеждата, че Путин ще ни пощади", source = "dnevnik.bg"),
            story(id = "tesla", title = "Tesla представи летящ Roadster", source = "dnevnik.bg"),
        )
        val briefing = BriefingEngine.build(
            stories = stories,
            options = BriefingOptions(BriefingPeriod.Hour, "All", hour),
            template = template,
            groupWithAi = { items ->
                seen.addAll(items)
                BriefingEngine.parseGrouping("""{"duplicates":[{"ids":["pm-a","pm-b"],"why":"Both report the joint statement"}]}""")
            },
        )
        assertEquals(listOf("pm-b"), seen.first { it.id == "pm-a" }.maybeSameAs)
        assertTrue(seen.first { it.id == "tesla" }.maybeSameAs.isEmpty())
        val page = BriefingPageReader.parse(briefing.html)!!
        val lead = page.stories.first { it.related.isNotEmpty() }
        assertEquals("Both report the joint statement", lead.duplicateWhy)
        assertEquals(1, lead.scores[Criterion.Coverage])
        assertEquals("Reported by Свободна точка, dnevnik.bg", lead.notes[Criterion.Coverage])

        // No AI answer: the title rule's guess merges them instead.
        val fallback = BriefingEngine.build(stories, BriefingOptions(BriefingPeriod.Hour, "All", hour), template, groupWithAi = { error("down") })
        assertEquals(2, BriefingPageReader.parse(fallback.html)!!.stories.size)
    }

    @Test
    fun shipsTheAiSummaryWithEachLinePointingAtItsStoryFoldedReportsIncluded() = runBlocking {
        val bullets = BriefingEngine.parseDigest(
            """{"bullets":[{"kind":"critical","text":"Former PMs press Radev","ids":["pm-a","pm-b"]},
               {"kind":"odd","text":"Tesla flies","ids":["tesla","ghost"]},{"kind":"leading","text":""}]}""",
        )
        assertEquals(listOf(DigestKind.Critical, DigestKind.Leading), bullets.map { it.kind })
        assertTrue(BriefingEngine.parseDigest("no json").isEmpty())
        val briefing = BriefingEngine.build(
            stories = listOf(
                story(id = "pm-a", title = "Костов, Борисов, Петков и Денков призоваха Радев", source = "A"),
                story(id = "pm-b", title = "Бивши премиери призоваха Радев", source = "B"),
                story(id = "tesla", title = "Tesla представи летящ Roadster", source = "C"),
            ),
            options = BriefingOptions(BriefingPeriod.Hour, "All", hour),
            template = template,
            groupWithAi = { StoryGrouping(duplicates = listOf(listOf("pm-a", "pm-b"))) },
            digestWithAi = { items ->
                assertEquals(2, items.size)
                bullets
            },
        )
        val page = BriefingPageReader.parse(briefing.html)!!
        assertEquals(2, page.digest.size)
        assertEquals(1, page.digest[0].targets.size)
        assertTrue(page.stories[page.digest[0].targets.single()].title.startsWith("Костов"))
        assertTrue(page.stories[page.digest[1].targets.single()].title.startsWith("Tesla"))

        val glance = BriefingPageReader.glance(page, BriefingPageReader.rank(page, BriefingEngine.DEFAULT_WEIGHTS))
        assertEquals(listOf("Former PMs press Radev", "Tesla flies"), glance.map { it.text })
        assertFalse(glance.any { it.marked })
    }

    @Test
    fun writesThePageAndTheKeywordNotesInBulgarianWhenTheBriefingIsInBulgarian() = runBlocking {
        val realTemplate = File("src/main/assets/briefing/page.html").readText()
        val briefing = BriefingEngine.build(
            stories = listOf(story(title = "Нов закон за пътищата", source = "БНТ")),
            options = BriefingOptions(BriefingPeriod.Day, "Всички източници", hour, language = BriefingLanguage.Bulgarian),
            template = realTemplate,
        )
        assertTrue(briefing.record.title.startsWith("Дневен бюлетин · Всички източници"))
        assertTrue(briefing.html.contains("<html lang=\"bg\">"))
        assertTrue(briefing.html.contains("Как се оценяват новините"))
        assertFalse(briefing.html.contains("How stories are rated"))
        assertFalse(briefing.html.contains("{{"))
        val page = BriefingPageReader.parse(briefing.html)!!
        val story = page.stories.single()
        assertEquals("Съобщава само БНТ", story.notes[Criterion.Coverage])
        assertEquals("Не се споменават жертви, ранени, щети или загубени пари", story.notes[Criterion.Severity])
        assertEquals("Тежест", page.text.criteria[Criterion.Severity])
        assertEquals("Важно", page.text.levels[PriorityLevel.High])
        assertEquals("Накратко", page.say("atAGlance"))
        assertEquals("Защо „Важно“ · 65/100", page.say("why", "level" to "Важно", "score" to 65))
    }

    @Test
    fun letsASevereLocalEventHappeningNowBeCriticalButNotAGrimBackgroundFeature() {
        fun rated(severity: Int, scale: Int, urgency: Int) = scores(
            Criterion.Severity to severity, Criterion.Scale to scale, Criterion.Urgency to urgency,
            Criterion.Interest to 2, Criterion.Coverage to 2,
        )
        // A body found and a suspect held today, in one town.
        assertEquals(null, BriefingEngine.criticalCap(rated(3, 1, 3)))
        assertEquals(PriorityLevel.Critical, BriefingEngine.levelOf(BriefingEngine.scoreOf(rated(3, 1, 3))))
        // Child marriages in one far province, a long-running situation.
        assertEquals(BriefingEngine.CriticalCap.NotNow, BriefingEngine.criticalCap(rated(3, 1, 1)))
        val wideFeature = rated(3, 3, 1) + (Criterion.Interest to 3)
        assertTrue(BriefingEngine.rawScoreOf(wideFeature) >= BriefingEngine.CRITICAL_AT)
        assertFalse(BriefingEngine.levelOf(BriefingEngine.scoreOf(wideFeature)) == PriorityLevel.Critical)
        // Happening now but minor and local.
        assertEquals(BriefingEngine.CriticalCap.Minor, BriefingEngine.criticalCap(rated(2, 1, 3)))
        assertEquals("Под „Критично“: местна новина без тежки последици.", BriefingText.Bulgarian.page[BriefingEngine.CriticalCap.Minor.textKey])
        assertEquals("Impact", BriefingText.English.page["impact"])
    }

    @Test
    fun cleansSummariesOnThePageAndNoLongerAsksForImpacts() = runBlocking {
        val briefing = BriefingEngine.build(
            stories = listOf(story(title = "Airports close in Rome", summary = "Airports closed.\nFlights were")),
            options = BriefingOptions(BriefingPeriod.Hour, "All", hour),
            template = template,
        )
        assertEquals("Airports closed.", BriefingPageReader.parse(briefing.html)!!.stories.single().summary)
        val prompt = BriefingEngine.buildAiRatingPrompt(emptyList(), false, "")
        assertFalse(prompt.contains("impacts"))
    }

    @Test
    fun groupsAnEventThatBeganAndEndedInThePeriodResolutionFirst() {
        val grouping = BriefingEngine.parseGrouping(
            """{"duplicates":[["found","found-2"]],
               "storylines":[{"label":"Search","ids":["missing","found","other"]}],
               "resolutions":[{"ids":["missing","search","found-2"],"resolvedBy":"found-2","label":"Missing boy found",
                 "summary":"Reported missing at 9:10, found unharmed at 11:40."}]}""",
        )
        assertEquals(1, grouping.resolutions.size)
        val grouped = BriefingEngine.applyGrouping(
            listOf(
                entry("found", 70, ImpactDimension.Society), entry("found-2", 50, ImpactDimension.Society),
                entry("missing", 60, ImpactDimension.Society), entry("search", 40, ImpactDimension.Society),
                entry("other", 30, ImpactDimension.Society),
            ),
            grouping,
        )
        val roles = grouped.associate { it.story.id to it.resolution?.role }
        // "found-2" was folded into "found", so "found" resolves it.
        assertEquals(
            mapOf("found" to ResolutionRole.Resolution, "missing" to ResolutionRole.Start, "search" to ResolutionRole.Update, "other" to null),
            roles,
        )
        assertEquals("Reported missing at 9:10, found unharmed at 11:40.", grouped.first { it.story.id == "found" }.resolution?.summary)
        // Items in a resolution leave their storyline, which then has too few stories.
        assertEquals(listOf<String?>(null, null, null, null), grouped.map { it.storyline })
    }

    @Test
    fun ignoresAResolutionWithASingleStoryOrNoLabel() {
        val grouping = BriefingEngine.parseGrouping("""{"resolutions":[{"ids":["a"],"label":"x"},{"ids":["a","b"],"label":""}]}""")
        assertTrue(grouping.resolutions.isEmpty())
    }

    @Test
    fun shipsTheResolutionWithEachStoryAndTheLabelsForThePage() = runBlocking {
        val briefing = BriefingEngine.build(
            stories = listOf(
                story(id = "m", title = "Boy missing near Varna", publishedAt = at(13, 5)),
                story(id = "f", title = "Missing boy found unharmed", publishedAt = at(13, 50)),
            ),
            options = BriefingOptions(BriefingPeriod.Hour, "All", hour, language = BriefingLanguage.Bulgarian),
            template = template,
            groupWithAi = {
                BriefingEngine.parseGrouping("""{"resolutions":[{"ids":["m","f"],"resolvedBy":"f","label":"Изчезнало момче","summary":"Намерено."}]}""")
            },
        )
        val page = BriefingPageReader.parse(briefing.html)!!
        // Not folded up front although the titles share most words: the AI saw both.
        assertEquals(listOf(ResolutionRole.Resolution, ResolutionRole.Start), page.stories.mapNotNull { it.resolution?.role }.sorted())
        assertEquals("Разрешено", page.say("resolved"))
        assertEquals("Начало", page.say("roleStart"))
        val ranked = BriefingPageReader.rank(page, BriefingEngine.DEFAULT_WEIGHTS)
        val unit = (ranked.shown + ranked.more).single()
        assertTrue(unit.resolved)
        assertEquals("Изчезнало момче", unit.label)
        assertEquals(listOf(ResolutionRole.Resolution, ResolutionRole.Start), unit.entries.map { it.story.resolution!!.role })
        assertEquals(listOf("Намерено."), BriefingPageReader.glance(page, ranked).map { it.text })
    }

    @Test
    fun coversAWeekAMonthOrACustomNumberOfHoursAndGrowsThePageWithThePeriod() {
        val now = at(14)
        fun hoursOf(window: BriefingWindow) = (window.endMs - window.startMs) / 3_600_000
        assertEquals(168, hoursOf(BriefingEngine.rollingWindow(BriefingPeriod.Week, now)))
        assertEquals(720, hoursOf(BriefingEngine.rollingWindow(BriefingPeriod.Month, now)))
        assertEquals(72, hoursOf(BriefingEngine.rollingWindow(BriefingPeriod.Custom, now, 72)))
        assertEquals(BriefingPeriod.MAX_CUSTOM_HOURS.toLong(), hoursOf(BriefingEngine.rollingWindow(BriefingPeriod.Custom, now, 99_999)))
        assertEquals(BriefingEngine.BriefingSize(12, 36), BriefingEngine.briefingSize(BriefingEngine.rollingWindow(BriefingPeriod.Hour, now)))
        assertEquals(BriefingEngine.BriefingSize(25, 70), BriefingEngine.briefingSize(BriefingEngine.rollingWindow(BriefingPeriod.Week, now)))
        assertEquals(BriefingEngine.BriefingSize(30, 80), BriefingEngine.briefingSize(BriefingEngine.rollingWindow(BriefingPeriod.Month, now)))
        val week = BriefingEngine.rollingWindow(BriefingPeriod.Week, now)
        assertTrue(
            Regex("^Седмичен бюлетин · Всички източници · .+ – .+$").matches(
                BriefingEngine.briefingTitle(BriefingPeriod.Week, "Всички източници", week, BriefingLanguage.Bulgarian),
            ),
        )
        assertEquals(BriefingPeriod.Month, BriefingPeriod.fromKey("month"))
    }

    @Test
    fun showsTheDateWithEachStoryTimeInABriefingLongerThanADay() = runBlocking {
        val window = BriefingWindow(at(14) - 7 * 86_400_000L, at(14))
        val briefing = BriefingEngine.build(
            listOf(story(title = "Old story", publishedAt = at(14) - 3 * 86_400_000L)),
            BriefingOptions(BriefingPeriod.Week, "All", window),
            template,
        )
        val time = BriefingPageReader.parse(briefing.html)!!.stories.single().time
        assertTrue(Regex("\\d{1,2}:\\d{2}").containsMatchIn(time))
        assertTrue(time.length > 5)
        assertEquals(5, BriefingPageReader.parse(BriefingEngine.build(listOf(story()), BriefingOptions(BriefingPeriod.Hour, "All", hour), template).html)!!.stories.single().time.length)
    }

    @Test
    fun asksForTheMainSurpriseOfAStoryNotASideNumber() {
        val prompt = BriefingEngine.buildAiRatingPrompt(emptyList(), true, "България")
        assertTrue(prompt.contains("main claim in the headline"))
        assertTrue(prompt.contains("a car that flies, not its acceleration"))
    }

    @Test
    fun everyBriefingPromptCarriesTheLiteralFactsRule() {
        val rule = BriefingEngine.LITERAL_FACTS_RULE
        assertTrue(BriefingEngine.buildAiRatingPrompt(emptyList(), true, "България").contains(rule))
        assertTrue(BriefingEngine.buildGroupingPrompt(emptyList(), BriefingLanguage.Bulgarian).contains(rule))
        assertTrue(BriefingEngine.buildDigestPrompt(emptyList(), BriefingLanguage.Bulgarian, "България").contains(rule))
        assertTrue(BriefingEngine.buildGroupingPrompt(emptyList(), BriefingLanguage.Bulgarian).contains("Write labels and reasons in Bulgarian"))
    }
}
