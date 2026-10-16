package com.ainews.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingPeriodPickerTest {
    @Test
    fun namesEveryStopTheWayTheMacDoes() {
        assertEquals(
            listOf(
                "Last hour", "Last 2 hours", "Last 3 hours", "Last 6 hours", "Last 12 hours", "Last day", "Last 2 days",
                "Last 3 days", "Last 5 days", "Last week", "Last 2 weeks", "Last 3 weeks", "Last month", "Last 2 months", "Last 3 months",
            ),
            CUSTOM_BRIEFING_HOURS.map(::briefingPeriodLabel),
        )
    }
}
