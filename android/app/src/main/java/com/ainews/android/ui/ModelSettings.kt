package com.ainews.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ainews.android.data.ModelCatalogState
import com.ainews.android.data.ModelKind
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The provider's live models, one choice per kind, each with its price. "Automatic" follows
 * the default picked by price and age; a picked model stays until the provider drops it.
 */
@Composable
internal fun ModelSettings(
    models: ModelCatalogState,
    keySaved: Boolean,
    onChoose: (ModelKind, String?) -> Unit,
    onRefresh: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                text = { Text("Automatic: newest model in this price range") },
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
