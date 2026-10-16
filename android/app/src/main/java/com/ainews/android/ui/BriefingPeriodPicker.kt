package com.ainews.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ainews.android.data.BriefingPeriod
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

/** What the button will write, e.g. "Last 3 days". */
internal fun chosenPeriodLabel(period: BriefingPeriod, customHours: Int): String =
    briefingPeriodLabel(period.hours ?: customHours)

private val PERIOD_CHOICES = listOf(
    BriefingPeriod.Hour to "Hour",
    BriefingPeriod.Day to "Day",
    BriefingPeriod.Week to "Week",
    BriefingPeriod.Month to "Month",
    BriefingPeriod.Custom to "Custom",
)

/**
 * One small form, as on the Mac: where the stories come from (only when there is a choice),
 * the period, a slider only for a custom length, and one button that says what it will
 * write. [scopeLabel] is the widget's feeds; without one the briefing covers every feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BriefingForm(
    scopeLabel: String?,
    enabled: Boolean,
    onWrite: (period: BriefingPeriod, customHours: Int?, allFeeds: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var period by rememberSaveable { mutableStateOf(BriefingPeriod.Day) }
    var allFeeds by rememberSaveable { mutableStateOf(scopeLabel == null) }
    // Starts on three days, as on the Mac.
    var stop by rememberSaveable { mutableFloatStateOf(7f) }
    val customHours = CUSTOM_BRIEFING_HOURS[stop.roundToInt().coerceIn(0, CUSTOM_BRIEFING_HOURS.lastIndex)]
    val scopeText = if (allFeeds || scopeLabel == null) "All feeds" else scopeLabel
    val label = chosenPeriodLabel(period, customHours)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (scopeLabel != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Stories from")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to scopeLabel, true to "All feeds").forEachIndexed { index, (all, text) ->
                        SegmentedButton(
                            selected = allFeeds == all,
                            onClick = { allFeeds = all },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            icon = {},
                        ) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Period")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PERIOD_CHOICES.forEachIndexed { index, (choice, text) ->
                    SegmentedButton(
                        selected = period == choice,
                        onClick = { period = choice },
                        shape = SegmentedButtonDefaults.itemShape(index, PERIOD_CHOICES.size),
                        // No check mark: five segments need the room for their words.
                        icon = {},
                    ) { Text(text, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (period == BriefingPeriod.Custom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1 h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = stop,
                        onValueChange = { stop = it.roundToInt().toFloat() },
                        valueRange = 0f..CUSTOM_BRIEFING_HOURS.lastIndex.toFloat(),
                        steps = CUSTOM_BRIEFING_HOURS.size - 2,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("3 mo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            enabled = enabled,
            onClick = { onWrite(period, if (period == BriefingPeriod.Custom) customHours else null, allFeeds || scopeLabel == null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Write briefing · $label · $scopeText", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Text(
            "AI writes it now from that period's stories and opens it. Longer periods take a little longer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
