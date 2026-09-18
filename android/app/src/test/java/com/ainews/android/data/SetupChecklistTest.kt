package com.ainews.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {
    @Test
    fun checklistFlagsAiWhenNoProviderIsSetUp() {
        val state = NewsUiState(
            settings = RuntimeSettings(aiProvider = AiProvider.OpenAI, providerKeySaved = false),
        )

        val aiItem = setupChecklistItems(state).first { it.id == "ai" }

        assertFalse(aiItem.complete)
    }

    @Test
    fun checklistCountsStoredKeyOrLocalModeAsSetUp() {
        val withKey = NewsUiState(
            settings = RuntimeSettings(aiProvider = AiProvider.OpenAI, providerKeySaved = true),
        )
        val localOnly = NewsUiState(settings = RuntimeSettings(aiProvider = AiProvider.LocalOnly))

        assertTrue(setupChecklistItems(withKey).first { it.id == "ai" }.complete)
        assertTrue(setupChecklistItems(localOnly).first { it.id == "ai" }.complete)
    }

    @Test
    fun checklistRequiresStoriesAfterInstall() {
        val state = NewsUiState(stories = emptyList())

        val storiesItem = setupChecklistItems(state).first { it.id == "stories" }

        assertFalse(storiesItem.complete)
    }

    @Test
    fun availableTopicsAlwaysIncludeDefaults() {
        val state = NewsUiState(stories = emptyList())

        assertTrue("AI policy" in state.availableTopics)
        assertTrue("World" in state.availableTopics)
        assertTrue("Public health" in state.availableTopics)
    }
}
