package com.ainews.android.data

import java.util.Locale

/** The language a briefing is written in: the AI's text and every label around it. */
enum class BriefingLanguage(val key: String, val locale: Locale) {
    English("en", Locale.UK),
    Bulgarian("bg", Locale.forLanguageTag("bg-BG")),
    ;

    companion object {
        fun fromKey(key: String?): BriefingLanguage = entries.firstOrNull { it.key == key } ?: English
    }
}

/**
 * Everything the page, the in-app screen and the keyword notes say, in the briefing's
 * language, so labels never come out in English beside Bulgarian AI text. A copy of the
 * Mac widget's `UI` dictionary in briefing.ts; keep the two in step. [page] goes to the
 * page script and the in-app screen; "{n}"-style slots are filled there.
 */
class BriefingText(
    val dimensions: Map<ImpactDimension, String>,
    val criteria: Map<Criterion, String>,
    val criteriaText: Map<Criterion, String>,
    val levels: Map<PriorityLevel, String>,
    val angles: Map<ImpactAngle, String>,
    val presets: List<String>,
    val noHarmYet: (area: String) -> String,
    val figureOfSpeech: (quote: String, area: String) -> String,
    val severalCountries: (list: String) -> String,
    val reportedBy: (names: String) -> String,
    val onlyOne: (name: String) -> String,
    val oneSource: String,
    val zero: Map<Criterion, String>,
    val yourTopics: (count: Int) -> String,
    val yourRegion: String,
    val mentions: (parts: List<String>) -> String,
    val noneMentioned: (count: Int, region: String) -> String,
    val title: (period: BriefingPeriod) -> String,
    val countLine: (count: String, generated: String) -> String,
    val footer: (ai: Boolean) -> String,
    val importance: String,
    val importanceAria: String,
    val importanceHint: String,
    val close: String,
    val goTop: String,
    val goBottom: String,
    /** Default scope names when a briefing covers all feeds, the filtered feed or several. */
    val allFeeds: String,
    val filteredFeed: String,
    val feedCount: (count: Int) -> String,
    val page: Map<String, String>,
) {
    companion object {
        private val thresholds = Triple(BriefingEngine.CRITICAL_AT, BriefingEngine.HIGH_AT, BriefingEngine.NOTABLE_AT)

        val English = BriefingText(
            dimensions = mapOf(
                ImpactDimension.Security to "Security & conflict",
                ImpactDimension.Politics to "Politics & governance",
                ImpactDimension.Economy to "Economy & money",
                ImpactDimension.Health to "Health",
                ImpactDimension.Environment to "Environment & disasters",
                ImpactDimension.Society to "Society & justice",
                ImpactDimension.Technology to "Technology",
                ImpactDimension.Culture to "Culture & entertainment",
                ImpactDimension.Sports to "Sports",
                ImpactDimension.Other to "Other",
            ),
            criteria = Criterion.entries.associateWith { it.label },
            criteriaText = Criterion.entries.associateWith { it.text },
            levels = PriorityLevel.entries.associateWith { it.label },
            angles = ImpactAngle.entries.associateWith { it.label },
            presets = listOf("Balanced", "Critical first", "Interesting", "Close to home"),
            noHarmYet = { area -> "$area story, but no harm is reported yet" },
            figureOfSpeech = { quote, area -> "$quote — figure of speech in a ${area.lowercase(Locale.ROOT)} story" },
            severalCountries = { list -> "Involves several countries: $list" },
            reportedBy = { names -> "Reported by $names" },
            onlyOne = { name -> "Only $name reports it" },
            oneSource = "one source",
            zero = mapOf(
                Criterion.Severity to "No deaths, injuries, damage or money lost are mentioned",
                Criterion.Scale to "No country-wide or international reach is mentioned",
                Criterion.Urgency to "No deadline, live event or warning is mentioned",
                Criterion.Interest to "No first, record, surprise or new revelation in the headline",
            ),
            yourTopics = { count -> if (count > 1) "your topics" else "your topic" },
            yourRegion = "your region",
            mentions = { parts -> "Mentions ${parts.joinToString(" and ")}" },
            noneMentioned = { count, region -> "None of your $count topics${if (region.isNotBlank()) " or $region" else ""} is mentioned" },
            title = { period ->
                when (period) {
                    BriefingPeriod.Hour -> "Hourly briefing"
                    BriefingPeriod.Day -> "Daily briefing"
                    BriefingPeriod.Week -> "Weekly briefing"
                    BriefingPeriod.Month -> "Monthly briefing"
                    BriefingPeriod.Custom -> "Briefing"
                }
            },
            countLine = { count, generated -> "$count stories · generated $generated" },
            footer = { ai ->
                val (critical, high, notable) = thresholds
                """<strong>How stories are rated.</strong> Each story gets 0–3 on six criteria, each with the fact behind it.
    The sliders set how much each criterion counts compared with the others (they are remembered for every briefing).
    The score is 0–100: one strong criterion is enough to lift a story, and each further one lifts it more, so a deadly attack
    does not also need to be surprising to rank high. The +numbers show each criterion's share of the score.
    Critical is $critical+, High $high+, Notable $notable+, Routine below.
    ${if (ai) "The AI reads the stories together to merge reports of the same event under one story (open \"more reports\" to see them and why), to group different stories about one ongoing situation into a storyline, and to write the summary at the top." else "Reports with near-identical headlines are merged under one story (open \"more reports\" to see them)."}
    No single area takes more than about a third of the list.
    ${if (ai) "Severity, scale, urgency and interest are judged by AI; coverage and relevance come from your sources and topics." else "AI was off, so keyword rules did the rating."}"""
            },
            importance = "Importance",
            importanceAria = "Importance of each criterion",
            importanceHint = "How much each criterion counts. The stories re-rank as you slide.",
            close = "Close",
            goTop = "Go to top",
            goBottom = "Go to bottom",
            allFeeds = "All Feeds",
            filteredFeed = "Filtered Feed",
            feedCount = { count -> "$count Feeds" },
            page = mapOf(
                "of" to "{a} of {b}",
                "notChecked" to "not checked by AI",
                "keywordRated" to "keyword-rated",
                "notCheckedTip" to "The AI did not answer for this story, so keyword rules rated it; it is kept below High until the AI checks it",
                "keywordTip" to "AI is off, so keyword rules rated this story",
                "whyItMatters" to "Why it matters",
                "impact" to "Impact",
                "why" to "Why {level} · {score}/100",
                "cappedNotNow" to "Kept below Critical: nothing is happening right now (a background or slow-moving story).",
                "cappedMinor" to "Kept below Critical: local and without severe harm.",
                "moreOne" to "1 more report of this",
                "moreMany" to "{n} more reports of this",
                "storyline" to "Storyline",
                "differentStories" to "{n} different stories",
                "moreInStoryline" to "{n} more stories in this storyline",
                "moreUpdates" to "{n} more updates",
                "resolved" to "Resolved",
                "reports" to "{n} reports",
                "roleResolution" to "How it ended",
                "roleUpdate" to "Update",
                "roleStart" to "How it began",
                "atAGlance" to "At a glance",
                "writtenByAi" to "written by AI from the stories below",
                "alsoInteresting" to "Also interesting",
                "story" to "story",
                "empty" to "No new stories were published in this period.",
                "priority" to "Priority",
                "alsoWorth" to "Also worth knowing",
                "stories" to "Stories",
                "moreStories" to "More stories ({n})",
                "custom" to "Custom",
                "bandAria" to "{label} importance",
            ),
        )

        val Bulgarian = BriefingText(
            dimensions = mapOf(
                ImpactDimension.Security to "Сигурност и конфликти",
                ImpactDimension.Politics to "Политика и управление",
                ImpactDimension.Economy to "Икономика и пари",
                ImpactDimension.Health to "Здраве",
                ImpactDimension.Environment to "Природа и бедствия",
                ImpactDimension.Society to "Общество и правосъдие",
                ImpactDimension.Technology to "Технологии",
                ImpactDimension.Culture to "Култура и забавления",
                ImpactDimension.Sports to "Спорт",
                ImpactDimension.Other to "Друго",
            ),
            criteria = mapOf(
                Criterion.Severity to "Тежест",
                Criterion.Scale to "Обхват",
                Criterion.Urgency to "Спешност",
                Criterion.Interest to "Интерес",
                Criterion.Coverage to "Отразяване",
                Criterion.Relevance to "Значимост",
            ),
            criteriaText = mapOf(
                Criterion.Severity to "вреда за живот, безопасност, права или пари",
                Criterion.Scale to "колко хора засяга (местно, национално, международно)",
                Criterion.Urgency to "дали се случва сега или изисква действие скоро",
                Criterion.Interest to "колко е изненадващо, ново или разкриващо",
                Criterion.Coverage to "колко източника съобщават за същото събитие",
                Criterion.Relevance to "дали засяга вашите теми или регион",
            ),
            levels = mapOf(
                PriorityLevel.Critical to "Критично",
                PriorityLevel.High to "Важно",
                PriorityLevel.Notable to "Забележимо",
                PriorityLevel.Routine to "Рутинно",
            ),
            angles = mapOf(
                ImpactAngle.Economic to "Икономика",
                ImpactAngle.Political to "Политика",
                ImpactAngle.Social to "Общество",
                ImpactAngle.Security to "Сигурност",
            ),
            presets = listOf("Балансирано", "Първо критичното", "Интересно", "Близо до дома"),
            noHarmYet = { area -> "Новина от „$area“, но не се съобщава за вреди" },
            figureOfSpeech = { quote, area -> "$quote — в преносен смисъл, новина от „$area“" },
            severalCountries = { list -> "Засяга няколко държави: $list" },
            reportedBy = { names -> "Съобщават: $names" },
            onlyOne = { name -> "Съобщава само $name" },
            oneSource = "един източник",
            zero = mapOf(
                Criterion.Severity to "Не се споменават жертви, ранени, щети или загубени пари",
                Criterion.Scale to "Не се споменава национален или международен обхват",
                Criterion.Urgency to "Не се споменава срок, събитие на живо или предупреждение",
                Criterion.Interest to "В заглавието няма първо, рекорд, изненада или ново разкритие",
            ),
            yourTopics = { count -> if (count > 1) "вашите теми" else "вашата тема" },
            yourRegion = "вашия регион",
            mentions = { parts -> "Споменава ${parts.joinToString(" и ")}" },
            noneMentioned = { count, region -> "Не се споменава нито една от вашите $count теми${if (region.isNotBlank()) " или $region" else ""}" },
            title = { period ->
                when (period) {
                    BriefingPeriod.Hour -> "Часов бюлетин"
                    BriefingPeriod.Day -> "Дневен бюлетин"
                    BriefingPeriod.Week -> "Седмичен бюлетин"
                    BriefingPeriod.Month -> "Месечен бюлетин"
                    BriefingPeriod.Custom -> "Бюлетин"
                }
            },
            countLine = { count, generated -> "$count новини · създаден $generated" },
            footer = { ai ->
                val (critical, high, notable) = thresholds
                """<strong>Как се оценяват новините.</strong> Всяка новина получава 0–3 по шест критерия, всеки с факта зад оценката.
    Плъзгачите задават колко тежи всеки критерий спрямо другите (запомнят се за всеки бюлетин).
    Резултатът е 0–100: един силен критерий стига, за да издигне новината, а всеки следващ я издига още, така че смъртоносна атака
    не трябва да е и изненадваща, за да е високо. Числата +N показват дела на всеки критерий в резултата.
    Критично е $critical+, Важно $high+, Забележимо $notable+, Рутинно под това.
    ${if (ai) "AI чете новините заедно: обединява съобщенията за едно и също събитие под една новина (отворете „още източника“, за да ги видите и защо), групира различни новини за една ситуация в сюжет и пише обобщението най-горе." else "Съобщения с почти еднакви заглавия се обединяват под една новина (отворете „още източника“, за да ги видите)."}
    Никоя област не заема повече от около една трета от списъка.
    ${if (ai) "Тежестта, обхватът, спешността и интересът се оценяват от AI; отразяването и значимостта идват от вашите източници и теми." else "AI беше изключен, затова оценката е по ключови думи."}"""
            },
            importance = "Важност",
            importanceAria = "Важност на всеки критерий",
            importanceHint = "Колко тежи всеки критерий. Новините се пренареждат, докато местите плъзгачите.",
            close = "Затвори",
            goTop = "Нагоре",
            goBottom = "Надолу",
            allFeeds = "Всички източници",
            filteredFeed = "Филтрирани",
            feedCount = { count -> "$count източника" },
            page = mapOf(
                "of" to "{a} от {b}",
                "notChecked" to "не е проверено от AI",
                "keywordRated" to "оценено по ключови думи",
                "notCheckedTip" to "AI не отговори за тази новина, затова е оценена по ключови думи; остава под „Важно“, докато AI я провери",
                "keywordTip" to "AI е изключен, затова новината е оценена по ключови думи",
                "whyItMatters" to "Защо е важно",
                "impact" to "Въздействие",
                "why" to "Защо „{level}“ · {score}/100",
                "cappedNotNow" to "Под „Критично“: нищо не се случва в момента (фонова или бавно развиваща се тема).",
                "cappedMinor" to "Под „Критично“: местна новина без тежки последици.",
                "moreOne" to "още 1 източник за същото",
                "moreMany" to "още {n} източника за същото",
                "storyline" to "Сюжет",
                "differentStories" to "{n} различни новини",
                "moreInStoryline" to "още {n} новини в сюжета",
                "moreUpdates" to "още {n} развития",
                "resolved" to "Разрешено",
                "reports" to "{n} съобщения",
                "roleResolution" to "Развръзка",
                "roleUpdate" to "Развитие",
                "roleStart" to "Начало",
                "atAGlance" to "Накратко",
                "writtenByAi" to "написано от AI по новините по-долу",
                "alsoInteresting" to "Още интересно",
                "story" to "новина",
                "empty" to "В този период няма нови новини.",
                "priority" to "Приоритетни",
                "alsoWorth" to "Също си струва",
                "stories" to "Новини",
                "moreStories" to "Още новини ({n})",
                "custom" to "Ръчно",
                "bandAria" to "Важност: {label}",
            ),
        )

        fun of(language: BriefingLanguage): BriefingText = if (language == BriefingLanguage.Bulgarian) Bulgarian else English

        /** Fills "{name}" slots, as the page script does. */
        fun fill(template: String, vararg values: Pair<String, Any>): String =
            values.fold(template) { text, (key, value) -> text.replace("{$key}", value.toString()) }
    }
}
