package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePreferencesCodecTest {
    @Test
    fun monitorJsonRoundTripKeepsPersistedFields() {
        val monitors = listOf(
            NewsMonitor(
                id = "custom",
                sentence = "public vaccination availability",
                enabled = false,
                lastMatchStoryId = "story-1",
                lastMatchConfidence = 0.8,
                lastMatchExplanation = "Matched terms",
            ),
        )

        val decoded = RuntimePreferencesCodec.monitorsFromJson(
            RuntimePreferencesCodec.monitorsToJson(monitors),
        )

        assertEquals(monitors, decoded)
    }

    @Test
    fun feedSourceJsonRoundTripDropsBlankRows() {
        val json = """[
            {"id":"one","title":"One","url":"https://example.com/rss"},
            {"id":"blank","title":" ","url":"https://example.com/blank"}
        ]"""

        val decoded = RuntimePreferencesCodec.feedSourcesFromJson(json)

        assertEquals(1, decoded.size)
        assertEquals("one", decoded.single().id)
    }

    @Test
    fun invalidMonitorJsonFallsBackToDefaults() {
        val decoded = RuntimePreferencesCodec.monitorsFromJson("{")

        assertEquals(defaultNewsMonitors, decoded)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun invalidFeedJsonFallsBackToDefaults() {
        val decoded = RuntimePreferencesCodec.feedSourcesFromJson("{")

        assertEquals(defaultFeedSources, decoded)
        assertFalse(decoded.isEmpty())
    }

    @Test
    fun widgetPresetJsonRoundTripKeepsViewSettings() {
        val presets = listOf(
            WidgetPreset(
                id = "preset-1",
                name = "BBC Stack",
                feedSourceId = "bbc-world",
                layoutMode = WidgetLayoutMode.Stack,
                backgroundMode = WidgetBackgroundMode.Transparent,
            ),
        )

        val decoded = RuntimePreferencesCodec.widgetPresetsFromJson(
            RuntimePreferencesCodec.widgetPresetsToJson(presets),
        )

        assertEquals(presets, decoded)
    }

    @Test
    fun invalidWidgetPresetJsonFallsBackToEmptyList() {
        val decoded = RuntimePreferencesCodec.widgetPresetsFromJson("{")

        assertTrue(decoded.isEmpty())
    }
}
