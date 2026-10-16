package com.ainews.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ainews.android.data.ModelCatalogState
import com.ainews.android.data.ModelKind
import com.ainews.android.data.ModelPolicy
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** What each model policy does, naming the models it would use from the current list. */
private fun policyText(models: ModelCatalogState, policy: ModelPolicy): String {
    fun now(policy: ModelPolicy, kind: ModelKind) = models.preview(policy, kind) ?: "not known until the model list is read"
    return when (policy) {
        ModelPolicy.Cheapest ->
            "Every AI task (summaries, questions, research and briefings) uses the cheapest model of your provider's " +
                "newest generation, now ${now(policy, ModelKind.Summary)}. Lowest cost: a briefing costs about a cent. " +
                "Analysis can be less sharp."
        ModelPolicy.Balanced ->
            "Summaries and questions use the newest low-cost model, now ${now(policy, ModelKind.Summary)}. Research and " +
                "briefings use the newest mid-priced model, now ${now(policy, ModelKind.Research)}: sharper analysis, at more " +
                "than ten times the cost for those tasks (a briefing costs about 15 cents)."
    }
}

/**
 * The model choice (cheapest or balanced), then the provider's live models, one choice per
 * kind, each with its price. "Automatic" follows the model choice; a picked model stays
 * until the provider drops it.
 */
@Composable
internal fun ModelSettings(
    models: ModelCatalogState,
    keySaved: Boolean,
    onPolicy: (ModelPolicy) -> Unit,
    onChoose: (ModelKind, String?) -> Unit,
    onRefresh: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Model choice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ModelPolicy.entries.forEach { policy ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().clickable { if (policy != models.policy) onPolicy(policy) },
            ) {
                RadioButton(selected = models.policy == policy, onClick = { if (policy != models.policy) onPolicy(policy) })
                Column {
                    Text(
                        policy.label + if (policy == ModelPolicy.DEFAULT) " (default)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(policyText(models, policy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "The models follow your provider's current list. Picking a model below overrides this for that task; " +
                "changing this choice clears those picks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val status = when {
            !keySaved -> "Save a provider key to see its models."
            models.refreshing -> "Reading the model list…"
            models.models.isEmpty() -> models.error ?: "No model list yet."
            else -> "${models.models.size} models from ${models.provider?.name.orEmpty()}, read " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(models.fetchedAtMs)) +
                (models.error?.let { " · last refresh failed: $it" } ?: "")
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (models.models.isNotEmpty()) {
            ModelKind.entries.forEach { kind ->
                val choice = models.choices[kind]
                val info = models.info(choice?.model)
                var open by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(kind.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(kind.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    (choice?.model ?: "—") + if (choice?.byUser == true) "" else " (automatic)",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                info?.let { Text(it.costLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                            DropdownMenuItem(
                                text = { Text("Automatic (follows the model choice above)") },
                                onClick = {
                                    open = false
                                    onChoose(kind, null)
                                },
                            )
                            models.models.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.id, style = MaterialTheme.typography.bodyMedium)
                                            Text(model.costLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        open = false
                                        onChoose(kind, model.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { scope.launch { onRefresh() } }, enabled = keySaved && !models.refreshing) {
            Text("Refresh model list")
        }
    }
}
