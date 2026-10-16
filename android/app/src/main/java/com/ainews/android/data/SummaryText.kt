package com.ainews.android.data

/*
 * Story summaries are asked for as one short sentence, but models do not always comply:
 * some write two or three sentences, some run out of room mid-word, some write a sentence
 * explaining that there is nothing to add and then the NO_SUMMARY marker. Every summary goes
 * through here when it is stored and when it is read back, so the widget, the app, the story
 * screen and the briefings all show the same clean text. A port of the Mac widget's
 * summaryText.ts; keep the two in step.
 */
object SummaryText {
    private val SENTENCE_END = Regex("[.!?…][\"“”»)]?(?=\\s|$)")
    private val WHITESPACE = Regex("\\s+")
    private val ENDS_WHOLE = Regex("[.!?…\"“”»)]$")
    private val NO_SUMMARY = Regex("NO_SUMMARY", RegexOption.IGNORE_CASE)
    private val BILINGUAL_SPLIT = Regex("\\s/\\s")

    /** The prompt asks for at most 22 words. */
    private const val MAX_WORDS = 22

    private fun words(text: String): Int = text.split(WHITESPACE).count { it.isNotEmpty() }

    /**
     * Text cut off mid-sentence keeps only its whole sentences; line breaks become spaces.
     * A single sentence without a final full stop is left as it is.
     */
    fun wholeSentences(text: String): String {
        val flat = text.replace(WHITESPACE, " ").trim()
        if (flat.isEmpty() || ENDS_WHOLE.containsMatchIn(flat)) return flat
        val end = listOf('.', '!', '?', '…').maxOf { flat.lastIndexOf(it) }
        return if (end > 0) flat.substring(0, end + 1) else flat
    }

    /** The first sentence, plus the next ones while the whole stays within [MAX_WORDS]. */
    private fun shortSummary(text: String): String {
        val whole = wholeSentences(text)
        val ends = SENTENCE_END.findAll(whole).map { it.range.last + 1 }.toList()
        if (ends.size <= 1) return whole
        var keep = ends.first()
        for (end in ends.drop(1)) {
            if (words(whole.substring(0, end)) > MAX_WORDS) break
            keep = end
        }
        return whole.substring(0, keep).trim()
    }

    /**
     * A clean summary, or "" when the model said it had nothing to add. Bilingual summaries
     * ("Bulgarian / English") are cleaned part by part.
     */
    fun cleanSummary(raw: String?): String {
        val text = raw.orEmpty().trim()
        if (text.isEmpty() || NO_SUMMARY.containsMatchIn(text)) return ""
        return text.split(BILINGUAL_SPLIT).map(::shortSummary).filter { it.isNotEmpty() }.joinToString(" / ")
    }
}
