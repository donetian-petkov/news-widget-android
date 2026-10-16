package com.ainews.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/** Stops for a custom briefing, from one hour to the 90 days stories are read back. The Mac's stops. */
internal val CUSTOM_BRIEFING_HOURS = listOf(1, 2, 3, 6, 12, 24, 48, 72, 120, 168, 336, 504, 720, 1440, 2160)

/** "Last 3 days", "Last week", "Last 2 months". */
internal fun briefingPeriodLabel(hours: Int): String {
    if (hours < 24) return if (hours == 1) "Last hour" else "Last $hours hours"
    val days = hours / 24
    if (days % 30 == 0) return if (days == 30) "Last month" else "Last ${days / 30} months"
    if (days % 7 == 0) return if (days == 7) "Last week" else "Last ${days / 7} weeks"
    return if (days == 1) "Last day" else "Last $days days"
}

/** Any period from an hour to 90 days: slide to the length, then write it. */
@Composable
internal fun CustomBriefingPeriod(enabled: Boolean, scope: String, onWrite: (hours: Int) -> Unit) {
    // Starts on three days, as on the Mac.
    var stop by rememberSaveable { mutableFloatStateOf(7f) }
    val hours = CUSTOM_BRIEFING_HOURS[stop.roundToInt().coerceIn(0, CUSTOM_BRIEFING_HOURS.lastIndex)]
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Custom period", style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOf(briefingPeriodLabel(hours), scope).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(enabled = enabled, onClick = { onWrite(hours) }) { Text("Write") }
        }
        Slider(
            value = stop,
            onValueChange = { stop = it.roundToInt().toFloat() },
            valueRange = 0f..CUSTOM_BRIEFING_HOURS.lastIndex.toFloat(),
            steps = CUSTOM_BRIEFING_HOURS.size - 2,
        )
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
