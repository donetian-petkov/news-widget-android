package com.ainews.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {
    @Test
    fun checklistMarksAiCompleteForLocalOnlyMode() {
        val state = NewsUiState(
            settings = RuntimeSettings(aiProvider = AiProvider.LocalOnly),
        )

        val aiItem = setupChecklistItems(state).first { it.id == "ai" }

        assertTrue(aiItem.complete)
    }

    @Test
    fun checklistRequiresStoriesAfterInstall() {
        val state = NewsUiState(stories = emptyList())

        val storiesItem = setupChecklistItems(state).first { it.id == "stories" }

        assertFalse(storiesItem.complete)
    }
}
