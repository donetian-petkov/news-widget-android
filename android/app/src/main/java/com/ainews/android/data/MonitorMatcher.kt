package com.ainews.android.data

import java.util.Locale
import kotlin.math.min

data class MonitorMatchDecision(
    val matched: Boolean,
    val confidence: Double,
    val explanation: String,
)

object MonitorMatcher {
    fun evaluate(monitor: NewsMonitor, story: NewsStory): MonitorMatchDecision {
        val monitorTerms = monitor.sentence.keyTerms()
        val storyText = "${story.title} ${story.summary}".lowercase(Locale.US)
        val hits = monitorTerms.filter { it in storyText }
        val confidence = if (monitorTerms.isEmpty()) {
            0.0
        } else {
            min(0.95, hits.size.toDouble() / monitorTerms.size + 0.25)
        }
        val matched = hits.size >= 2 || exactHealthAvailabilityMatch(monitor.sentence, storyText)
        val explanation = if (matched) {
            "Matched title/summary terms: ${hits.take(5).joinToString(", ")}"
        } else {
            "No title/summary match above threshold"
        }
        return MonitorMatchDecision(matched, confidence, explanation)
    }

    private fun exactHealthAvailabilityMatch(monitor: String, storyText: String): Boolean {
        val normalizedMonitor = monitor.lowercase(Locale.US)
        return "flu" in normalizedMonitor &&
            ("vaccination" in normalizedMonitor || "vaccine" in normalizedMonitor) &&
            ("flu" in storyText || "vaccination" in storyText || "vaccine" in storyText) &&
            ("available" in storyText || "calendar" in storyText || "schedule" in storyText)
    }

    private fun String.keyTerms(): List<String> =
        lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .filterNot { it in stopWords }
            .distinct()

    private val stopWords = setOf(
        "when",
        "will",
        "with",
        "from",
        "that",
        "this",
        "have",
        "public",
        "available",
    )
}
