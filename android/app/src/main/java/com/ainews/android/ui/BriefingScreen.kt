package com.ainews.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ainews.android.R
import com.ainews.android.data.BriefingEngine
import com.ainews.android.data.BriefingPage
import com.ainews.android.data.BriefingPageReader
import com.ainews.android.data.BriefingUnit
import com.ainews.android.data.Criterion
import com.ainews.android.data.GlanceLine
import com.ainews.android.data.ImpactAngle
import com.ainews.android.data.PriorityLevel
import com.ainews.android.data.RankedStory
import com.ainews.android.data.ResolutionRole
import com.ainews.android.network.ImageDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What the list shows, in order, so a summary line can scroll to the card it names. */
private sealed interface ListRow {
    data object Header : ListRow
    data object Glance : ListRow
    data object Empty : ListRow
    data class Section(val title: String) : ListRow
    data class Unit(val unit: BriefingUnit) : ListRow
    data object MoreToggle : ListRow
}

/**
 * A saved briefing as a native screen, matching the web page: the summary at the top, the
 * stories ranked with the reader's weights, storylines framed as one group, the score
 * breakdowns folded away, the criteria in a side menu, and every word in the briefing's
 * language. The interactive web page is one tap away for the browser.
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
    val glance = remember(page, ranked) { BriefingPageReader.glance(page, ranked) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    val priority = ranked.shown.filter { it.lead.level == PriorityLevel.Critical || it.lead.level == PriorityLevel.High }
    val others = ranked.shown - priority.toSet()
    val shownCount = ranked.shown.sumOf { it.entries.size }

    val rows = buildList {
        add(ListRow.Header)
        if (page.stories.isEmpty()) add(ListRow.Empty)
        if (glance.isNotEmpty()) add(ListRow.Glance)
        if (priority.isNotEmpty()) {
            add(ListRow.Section(page.say("priority")))
            priority.forEach { add(ListRow.Unit(it)) }
        }
        if (others.isNotEmpty()) {
            add(ListRow.Section(page.say(if (priority.isEmpty()) "stories" else "alsoWorth")))
            others.forEach { add(ListRow.Unit(it)) }
        }
        if (ranked.more.isNotEmpty()) {
            add(ListRow.MoreToggle)
            if (showMore) ranked.more.forEach { add(ListRow.Unit(it)) }
        }
    }
    // A summary line pointing at a story folded under "more" opens that list first.
    LaunchedEffect(jumpTo, rows) {
        val target = jumpTo ?: return@LaunchedEffect
        val at = rows.indexOfFirst { it is ListRow.Unit && it.unit.entries.any { entry -> entry.story.index == target } }
        if (at >= 0) {
            listState.animateScrollToItem(at)
            jumpTo = null
        } else if (!showMore && ranked.more.any { unit -> unit.entries.any { it.story.index == target } }) {
            showMore = true
        } else {
            jumpTo = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ImportancePanel(page = page, weights = weights, onWeightsChange = onWeightsChange)
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
                            Icon(painterResource(R.drawable.ic_tune), contentDescription = page.text.importanceAria, modifier = Modifier.size(22.dp))
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
                        Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = page.text.goTop, modifier = Modifier.size(20.dp))
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val last = listState.layoutInfo.totalItemsCount - 1
                                if (last >= 0) listState.animateScrollToItem(last)
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = page.text.goBottom, modifier = Modifier.size(20.dp))
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
                items(rows) { row ->
                    when (row) {
                        ListRow.Header -> BriefingSummary(page, shownCount, ranked.shown)
                        ListRow.Empty -> Text(page.say("empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ListRow.Glance -> GlanceCard(page, glance, onJump = { jumpTo = it })
                        is ListRow.Section -> SectionTitle(row.title)
                        is ListRow.Unit -> UnitBlock(page, row.unit, weights, onOpenLink)
                        ListRow.MoreToggle -> TextButton(onClick = { showMore = !showMore }) {
                            Text("${if (showMore) "▾" else "▸"} " + page.say("moreStories", "n" to ranked.more.sumOf { it.entries.size }))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingSummary(page: BriefingPage, shown: Int, units: List<BriefingUnit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = page.say("of", "a" to shown, "b" to page.storyCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val mix = units.flatMap { it.entries }.groupingBy { it.story.dimensionLabel }.eachCount().entries.sortedByDescending { it.value }
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

private fun curiousColor(dark: Boolean): Color = if (dark) Color(0xFFA78BFA) else Color(0xFF6A4CC7)

/**
 * "At a glance": the AI's summary when it wrote one, otherwise the top stories' own
 * summaries. Each dot takes the level colour of the strongest story it points to; tapping
 * a line scrolls to its card.
 */
@Composable
private fun GlanceCard(page: BriefingPage, lines: List<GlanceLine>, onJump: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.say("atAGlance").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (page.digest.isNotEmpty()) {
                    Text(
                        "  " + page.say("writtenByAi"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            lines.forEach { line ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = line.targets.isNotEmpty()) { line.targets.firstOrNull()?.let(onJump) },
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp, end = 10.dp)
                            .size(7.dp)
                            .background(levelColor(line.level, dark), CircleShape),
                    )
                    Column {
                        if (line.marked) {
                            Text(page.say("alsoInteresting"), color = curiousColor(dark), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                        if (line.targets.size > 1) {
                            Text(
                                line.targets.joinToString("  ") { index -> "→ " + (page.stories.getOrNull(index)?.source?.ifBlank { null } ?: page.say("story")) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
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

private fun storylineColor(dark: Boolean): Color = if (dark) Color(0xFF4652A8) else Color(0xFF4B5BBD)

private fun storylineBackground(dark: Boolean): Color = if (dark) Color(0xFF1C1F33) else Color(0xFFECEEFB)

/**
 * A storyline is framed in its own colour with a header bar, so it reads as one group and
 * it is obvious where it ends and the next, unrelated story begins.
 */
@Composable
private fun UnitBlock(page: BriefingPage, unit: BriefingUnit, weights: Map<Criterion, Double>, onOpenLink: (String) -> Unit) {
    if (unit.entries.size < 2) {
        StoryCard(page, unit.lead, weights, onOpenLink)
        return
    }
    if (unit.resolved) {
        ResolvedBlock(page, unit, weights, onOpenLink)
        return
    }
    val dark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = storylineBackground(dark),
        border = BorderStroke(2.dp, storylineColor(dark)),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(storylineColor(dark)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    page.say("storyline").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.width(10.dp))
                Text(unit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Text(
                    page.say("differentStories", "n" to unit.entries.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (unit.why.isNotBlank()) {
                    Text(unit.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Up to three stories all show; a longer storyline shows its two strongest.
                val shown = if (unit.entries.size <= 3) unit.entries.size else 2
                unit.entries.take(shown).forEach { StoryCard(page, it, weights, onOpenLink) }
                FoldRest(page, "line-${unit.label}", unit.entries.drop(shown), "moreInStoryline", storylineColor(dark)) { entry ->
                    StoryCard(page, entry, weights, onOpenLink)
                }
            }
        }
    }
}

private fun resolvedColor(dark: Boolean): Color = if (dark) Color(0xFF2F8F57) else Color(0xFF1F7A45)

private fun resolvedBackground(dark: Boolean): Color = if (dark) Color(0xFF15241B) else Color(0xFFE9F5EE)

/**
 * An event that began and ended in the period: green frame, the summary from start to
 * finish on top, then the report that ended it, the updates from latest to earliest, and
 * the first report last, each marked with its role and time.
 */
@Composable
private fun ResolvedBlock(page: BriefingPage, unit: BriefingUnit, weights: Map<Criterion, Double>, onOpenLink: (String) -> Unit) {
    val dark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = resolvedBackground(dark),
        border = BorderStroke(2.dp, resolvedColor(dark)),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(resolvedColor(dark)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(page.say("resolved").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.width(10.dp))
                Text(unit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Text(page.say("reports", "n" to unit.entries.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (unit.resolutionSummary.isNotBlank()) {
                    Text(unit.resolutionSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                @Composable
                fun step(entry: RankedStory) {
                    val role = entry.story.resolution?.role ?: ResolutionRole.Update
                    val roleText = page.say(
                        when (role) {
                            ResolutionRole.Resolution -> "roleResolution"
                            ResolutionRole.Update -> "roleUpdate"
                            ResolutionRole.Start -> "roleStart"
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            (roleText + " · " + entry.story.time).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (role == ResolutionRole.Resolution) resolvedColor(dark) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        StoryCard(page, entry, weights, onOpenLink)
                    }
                }
                // How it ended and how it began stay in view; updates in between fold away when there are several.
                val ending = unit.entries.first()
                val others = unit.entries.drop(1).toMutableList()
                val beginning = others.lastOrNull()?.takeIf { it.story.resolution?.role == ResolutionRole.Start }?.also { others.removeAt(others.lastIndex) }
                step(ending)
                if (others.size <= 1) {
                    others.forEach { step(it) }
                } else {
                    FoldRest(page, "arc-${ending.story.resolution?.key}", others, "moreUpdates", resolvedColor(dark)) { step(it) }
                }
                beginning?.let { step(it) }
            }
        }
    }
}

/**
 * The strongest stories stay in view; the others fold away under one line, and a fold the
 * reader opened stays open while the screen is redrawn.
 */
@Composable
private fun FoldRest(
    page: BriefingPage,
    key: String,
    rest: List<RankedStory>,
    textKey: String,
    color: Color,
    draw: @Composable (RankedStory) -> Unit,
) {
    if (rest.isEmpty()) return
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Text(
        "${if (open) "▾" else "▸"} " + page.say(textKey, "n" to rest.size),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.clickable { open = !open }.padding(horizontal = 6.dp, vertical = 4.dp),
    )
    if (open) rest.forEach { draw(it) }
}

internal fun levelColor(level: PriorityLevel, dark: Boolean): Color = when (level) {
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
private fun StoryCard(page: BriefingPage, entry: RankedStory, weights: Map<Criterion, Double>, onOpenLink: (String) -> Unit) {
    val story = entry.story
    val text = page.text
    val dark = isSystemInDarkTheme()
    val accent = levelColor(entry.level, dark)
    val levelLabel = text.levels.getValue(entry.level)
    var showDupes by rememberSaveable(story.index) { mutableStateOf(false) }
    // The breakdown is folded away; the badge already says the level and score.
    var showWhy by rememberSaveable("why-${story.index}") { mutableStateOf(false) }
    // The impact notes fold away too; the heading names the angles inside.
    var showImpact by rememberSaveable("impact-${story.index}") { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Spacer(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (story.coverUrl.isNotBlank()) CoverImage(story.coverUrl)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$levelLabel · ${entry.score}", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "  " + listOf(story.source, story.time, story.dimensionLabel).filter { it.isNotBlank() }.joinToString(" · ") +
                            if (!story.ratedByAi) " · " + page.say(if (page.aiRan) "notChecked" else "keywordRated") else "",
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
                    val angles = ImpactAngle.entries.filter { it in story.impacts }
                    Text(
                        "${if (showImpact) "▾" else "▸"} " + page.say("impact") + " · " +
                            angles.joinToString(", ") { text.angles.getValue(it).lowercase(java.util.Locale.ROOT) },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showImpact = !showImpact }.padding(vertical = 2.dp),
                    )
                    if (showImpact) angles.forEach { angle ->
                        Row {
                            Text(
                                text.angles.getValue(angle).uppercase(),
                                color = angleColor(angle, dark),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(84.dp).padding(top = 2.dp),
                            )
                            Text(story.impacts.getValue(angle), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (story.whyItMatters.isNotBlank()) {
                    Text("${page.say("whyItMatters")}: ${story.whyItMatters}", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "${if (showWhy) "▾" else "▸"} " + page.say("why", "level" to levelLabel, "score" to entry.score),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showWhy = !showWhy }.padding(vertical = 2.dp),
                )
                if (showWhy) {
                    // Said when the cap, not the score, kept the story below Critical.
                    BriefingEngine.criticalCap(story.scores)
                        ?.takeIf { BriefingEngine.rawScoreOf(story.scores, weights) >= BriefingEngine.CRITICAL_AT }
                        ?.let { cap ->
                            Text(page.say(cap.textKey), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    val shares = BriefingPageReader.shares(story.scores, weights, entry.score)
                    Criterion.entries.forEach { criterion ->
                        val value = story.scores[criterion] ?: 0
                        Row(modifier = Modifier.alpha(if (value == 0) 0.55f else 1f)) {
                            Text(text.criteria.getValue(criterion), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
                            Text("●".repeat(value) + "○".repeat(3 - value), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                            Text("+${shares[criterion] ?: 0}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                            Text(
                                story.notes[criterion] ?: text.criteriaText.getValue(criterion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (story.related.isNotEmpty()) {
                    TextButton(onClick = { showDupes = !showDupes }, contentPadding = PaddingValues(0.dp)) {
                        val sources = story.related.map { it.source }.filter { it.isNotBlank() }.distinct()
                        Text(
                            "${if (showDupes) "▾" else "▸"} " +
                                page.say(if (story.related.size == 1) "moreOne" else "moreMany", "n" to story.related.size) +
                                if (sources.isNotEmpty()) " · " + sources.take(3).joinToString(", ") else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (showDupes) {
                        if (story.duplicateWhy.isNotBlank()) {
                            Text(
                                story.duplicateWhy,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
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
private fun ImportancePanel(page: BriefingPage, weights: Map<Criterion, Double>, onWeightsChange: (Map<Criterion, Double>) -> Unit) {
    val text = page.text
    Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text.importance, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text.importanceHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BriefingPageReader.presets.forEachIndexed { index, (name, preset) ->
                FilterChip(
                    selected = Criterion.entries.all { (weights[it] ?: 0.0) == (preset[it] ?: 0.0) },
                    onClick = { onWeightsChange(preset) },
                    label = { Text(text.presets.getOrElse(index) { name }) },
                )
            }
        }
        Criterion.entries.forEach { criterion ->
            val value = weights[criterion] ?: 0.0
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.criteria.getValue(criterion), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                Text(text.criteriaText.getValue(criterion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
