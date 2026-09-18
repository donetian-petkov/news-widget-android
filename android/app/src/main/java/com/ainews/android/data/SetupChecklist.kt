package com.ainews.android.data

data class SetupChecklistItem(
    val id: String,
    val title: String,
    val complete: Boolean,
)

fun setupChecklistItems(state: NewsUiState): List<SetupChecklistItem> =
    listOf(
        SetupChecklistItem(
            id = "runtime",
            title = "Turn on the runtime",
            complete = state.runtime.runtimeEnabled && state.runtime.fetchEnabled,
        ),
        SetupChecklistItem(
            id = "feeds",
            title = "Keep at least one RSS feed",
            complete = state.feedSources.isNotEmpty(),
        ),
        SetupChecklistItem(
            id = "stories",
            title = "Fetch stories",
            complete = state.stories.isNotEmpty(),
        ),
        SetupChecklistItem(
            id = "ai",
            title = "Choose AI mode or save a provider key",
            complete = state.settings.aiProvider == AiProvider.LocalOnly || state.settings.providerKeySaved,
        ),
    )
