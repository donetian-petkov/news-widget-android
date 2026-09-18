package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeStateTest {
    @Test
    fun poweredOffStatusShowsFetchPaused() {
        val state = RuntimeState(
            runtimeEnabled = false,
            lastFetchStatus = FetchStatus.Paused,
        )

        assertEquals("Runtime off - Fetch paused", state.statusText)
    }

    @Test
    fun fetchingStatusIsVisible() {
        val state = RuntimeState(
            runtimeEnabled = true,
            fetchEnabled = true,
            lastFetchStatus = FetchStatus.Fetching,
        )

        assertEquals("Runtime on - Fetching", state.statusText)
    }
}
