package com.ainews.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ainews.android.R
import com.ainews.android.data.BriefingPage
import com.ainews.android.data.BriefingPageReader
import com.ainews.android.data.BriefingUnit
import com.ainews.android.data.Criterion
import com.ainews.android.data.ImpactAngle
import com.ainews.android.data.PriorityLevel
import com.ainews.android.data.RankedStory
import com.ainews.android.network.ImageDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A saved briefing as a native screen: the stories ranked with the reader's weights, the
 * criteria in a side menu, buttons to jump to the top and bottom, and the interactive web
 * page one tap away for the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    page: BriefingPage,
    weights: Map<Criterion, Double>,
    onWeightsChange: (Map<Criterion, Double>) -> Unit,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ranked = remember(page, weights) { BriefingPageReader.rank(page, weights) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    val priority = ranked.shown.filter { it.lead.level == PriorityLevel.Critical || it.lead.level == PriorityLevel.High }
    val others = ranked.shown - priority.toSet()
    val shownCount = ranked.shown.sumOf { it.entries.size }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ImportancePanel(weights = weights, onWeightsChange = onWeightsChange)
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(page.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", modifier = Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(painterResource(R.drawable.ic_tune), contentDescription = "Importance of each criterion", modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = onOpenInBrowser) {
                            Icon(painterResource(R.drawable.ic_open), contentDescription = "Open the interactive page in the browser", modifier = Modifier.size(22.dp))
                        }
                    },
                )
            },
            floatingActionButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                        Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Go to top", modifier = Modifier.size(20.dp))
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val last = listState.layoutInfo.totalItemsCount - 1
                                if (last >= 0) listState.animateScrollToItem(last)
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Go to bottom", modifier = Modifier.size(20.dp))
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item { BriefingSummary(shownCount, page.storyCount, ranked.shown) }
                if (page.stories.isEmpty()) {
                    item { Text("No new stories were published in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (priority.isNotEmpty()) {
                    item { SectionTitle("Priority") }
                    items(priority) { unit -> UnitBlock(unit, weights, onOpenLink) }
                }
                if (others.isNotEmpty()) {
                    item { SectionTitle(if (priority.isEmpty()) "Stories" else "Also worth knowing") }
                    items(others) { unit -> UnitBlock(unit, weights, onOpenLink) }
                }
                if (ranked.more.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showMore = !showMore }) {
                            Text("${if (showMore) "Hide" else "Show"} ${ranked.more.sumOf { it.entries.size }} more stories")
                        }
                    }
                    if (showMore) {
                        items(ranked.more) { unit -> UnitBlock(unit, weights, onOpenLink) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingSummary(shown: Int, total: Int, units: List<BriefingUnit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$shown of $total stories · tap ⚙ for what counts most",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val mix = units.flatMap { it.entries }.groupingBy { it.story.dimension.label }.eachCount().entries.sortedByDescending { it.value }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            mix.forEach { (label, count) ->
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "$label $count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun UnitBlock(unit: BriefingUnit, weights: Map<Criterion, Double>, onOpenLink: (String) -> Unit) {
    if (unit.entries.size < 2) {
        StoryCard(unit.lead, weights, onOpenLink)
        return
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(unit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${unit.entries.size} stories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            unit.entries.forEach { StoryCard(it, weights, onOpenLink) }
        }
    }
}

private fun levelColor(level: PriorityLevel, dark: Boolean): Color = when (level) {
    PriorityLevel.Critical -> if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828)
    PriorityLevel.High -> if (dark) Color(0xFFFFA94D) else Color(0xFFE07A00)
    PriorityLevel.Notable -> if (dark) Color(0xFF7AA7FF) else Color(0xFF2F6FDE)
    PriorityLevel.Routine -> Color(0xFF8A919C)
}

private fun angleColor(angle: ImpactAngle, dark: Boolean): Color = when (angle) {
    ImpactAngle.Economic -> if (dark) Color(0xFF4ADE80) else Color(0xFF1F8A4C)
    ImpactAngle.Political -> if (dark) Color(0xFFA78BFA) else Color(0xFF6A4CC7)
    ImpactAngle.Social -> if (dark) Color(0xFFFB923C) else Color(0xFFC2410C)
    ImpactAngle.Security -> if (dark) Color(0xFFF87171) else Color(0xFFB42318)
}

@Composable
private fun StoryCard(entry: RankedStory, weights: Map<Criterion, Double>, onOpenLink: (String) -> Unit) {
    val story = entry.story
    val dark = isSystemInDarkTheme()
    val accent = levelColor(entry.level, dark)
    var showDupes by rememberSaveable(story.link + story.title) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Spacer(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (story.coverUrl.isNotBlank()) CoverImage(story.coverUrl)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.level.label} · ${entry.score}", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "  " + listOf(story.source, story.time, story.dimension.label).filter { it.isNotBlank() }.joinToString(" · ") +
                            if (!story.ratedByAi) " · keyword-rated" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = story.link.isNotBlank()) { onOpenLink(story.link) },
                )
                if (story.summary.isNotBlank()) Text(story.summary, style = MaterialTheme.typography.bodyMedium)

                if (story.impacts.isNotEmpty()) {
                    ImpactAngle.entries.filter { it in story.impacts }.forEach { angle ->
                        Row {
                            Text(
                                angle.label.uppercase(),
                                color = angleColor(angle, dark),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(78.dp).padding(top = 2.dp),
                            )
                            Text(story.impacts.getValue(angle), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (story.whyItMatters.isNotBlank()) {
                    Text("Why it matters: ${story.whyItMatters}", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Rated ${entry.level.label.lowercase()} (${entry.score}/100) because:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shares = BriefingPageReader.shares(story.scores, weights, entry.score)
                Criterion.entries.forEach { criterion ->
                    val value = story.scores[criterion] ?: 0
                    Row(modifier = Modifier.alpha(if (value == 0) 0.55f else 1f)) {
                        Text(criterion.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(76.dp))
                        Text("●".repeat(value) + "○".repeat(3 - value), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                        Text("+${shares[criterion] ?: 0}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                        Text(
                            story.notes[criterion] ?: criterion.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (story.related.isNotEmpty()) {
                    TextButton(onClick = { showDupes = !showDupes }, contentPadding = PaddingValues(0.dp)) {
                        val sources = story.related.map { it.source }.filter { it.isNotBlank() }.distinct()
                        Text(
                            "${if (showDupes) "▾" else "▸"} ${story.related.size} more ${if (story.related.size == 1) "report" else "reports"} of this" +
                                if (sources.isNotEmpty()) " · " + sources.take(3).joinToString(", ") else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (showDupes) {
                        story.related.forEach { other ->
                            Column(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable(enabled = other.link.isNotBlank()) { onOpenLink(other.link) },
                            ) {
                                Text(
                                    listOf(other.source, other.time).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(other.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverImage(url: String) {
    val context = LocalContext.current
    val cache = remember { ImageDiskCache() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) { runCatching { cache.loadBitmap(context, url)?.asImageBitmap() }.getOrNull() }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
    }
}

/** The side menu: how much each criterion counts, with presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportancePanel(weights: Map<Criterion, Double>, onWeightsChange: (Map<Criterion, Double>) -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Importance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "How much each criterion counts. The stories re-rank as you slide.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BriefingPageReader.presets.forEach { (name, preset) ->
                FilterChip(
                    selected = Criterion.entries.all { (weights[it] ?: 0.0) == (preset[it] ?: 0.0) },
                    onClick = { onWeightsChange(preset) },
                    label = { Text(name) },
                )
            }
        }
        Criterion.entries.forEach { criterion ->
            val value = weights[criterion] ?: 0.0
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(criterion.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { slid -> onWeightsChange(weights + (criterion to (slid * 2).roundToInt() / 2.0)) },
                    valueRange = 0f..5f,
                    steps = 9,
                )
                Text(criterion.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
