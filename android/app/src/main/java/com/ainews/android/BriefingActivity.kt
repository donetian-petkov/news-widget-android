package com.ainews.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.ainews.android.data.BriefingEngine
import com.ainews.android.data.BriefingPageReader
import com.ainews.android.data.BriefingPeriod
import com.ainews.android.data.Criterion
import com.ainews.android.data.NewsRepository
import com.ainews.android.ui.BriefingScreen
import java.io.File
import kotlinx.coroutines.launch

/**
 * Shows a briefing as a native screen, or, opened from a widget, offers an hourly or daily
 * briefing for that widget's feeds and writes the one picked. The same briefing also opens
 * as the interactive web page the Mac widget uses, in the browser.
 */
class BriefingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NewsRepository.initialize(this)
        val feedSourceIds = intent.getStringArrayExtra(EXTRA_FEED_IDS)?.toSet().orEmpty()
        val scopeLabel = intent.getStringExtra(EXTRA_SCOPE_LABEL) ?: "All Feeds"
        val initialId = intent.getStringExtra(EXTRA_BRIEFING_ID)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BriefingContent(initialId, feedSourceIds, scopeLabel)
                }
            }
        }
    }

    @Composable
    private fun BriefingContent(initialId: String?, feedSourceIds: Set<String>, scopeLabel: String) {
        var briefingId by remember { mutableStateOf(initialId) }
        var html by remember { mutableStateOf(initialId?.let(NewsRepository::briefingHtml)) }
        var showWebPage by remember { mutableStateOf(false) }
        var weights by remember { mutableStateOf(loadWeights()) }
        var working by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun write(period: BriefingPeriod) {
            working = true
            failed = null
            scope.launch {
                val record = NewsRepository.writeBriefing(period, feedSourceIds)
                briefingId = record?.id
                html = record?.id?.let(NewsRepository::briefingHtml)
                if (html == null) failed = NewsRepository.state.value.message ?: "The briefing could not be written."
                working = false
            }
        }

        val pageHtml = html
        val page = remember(pageHtml) { pageHtml?.let(BriefingPageReader::parse) }
        BackHandler(enabled = showWebPage && page != null) { showWebPage = false }
        when {
            pageHtml != null && (showWebPage || page == null) -> BriefingPage(pageHtml)
            page != null -> BriefingScreen(
                page = page,
                weights = weights,
                onWeightsChange = { changed ->
                    weights = changed
                    saveWeights(changed)
                },
                onBack = { finish() },
                onOpenInBrowser = {
                    // The interactive page in the browser; without one, the same page in the app.
                    if (briefingId?.let(::openPageInBrowser) != true) showWebPage = true
                },
                onOpenLink = { link -> openInBrowser(this@BriefingActivity, Uri.parse(link)) },
            )
            working -> Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Rating and grouping the stories…",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Briefing for $scopeLabel", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Stories rated by how much they matter, with the reason for each score, " +
                        "their economic, political, social and security impact, and sliders to set what counts most.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { write(BriefingPeriod.Hour) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hourly briefing")
                }
                Button(onClick = { write(BriefingPeriod.Day) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Daily briefing")
                }
                NewsRepository.latestBriefing()?.let { latest ->
                    OutlinedButton(
                        onClick = {
                            briefingId = latest.id
                            html = NewsRepository.briefingHtml(latest.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Latest saved briefing")
                    }
                }
                failed?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun BriefingPage(html: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            factory = { context ->
                WebView(context).apply {
                    // The sliders and the page script need JavaScript; storage keeps the
                    // reader's slider settings across briefings.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openInBrowser(context, request.url)
                            return true
                        }
                    }
                    // One fixed origin for every briefing, so the saved slider settings are shared.
                    loadDataWithBaseURL(PAGE_ORIGIN, html, "text/html", "utf-8", null)
                }
            },
        )
    }

    private fun openInBrowser(context: Context, url: Uri) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
    }

    /** Hands the saved page to the browser through a read-only content link. */
    private fun openPageInBrowser(briefingId: String): Boolean = runCatching {
        val file = File(File(filesDir, "briefings"), "$briefingId.html")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/html")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }.isSuccess

    /** The reader's slider settings, kept for every briefing. */
    private fun loadWeights(): Map<Criterion, Double> {
        val prefs = getSharedPreferences(WEIGHTS_PREFS, MODE_PRIVATE)
        return Criterion.entries.associateWith { criterion ->
            prefs.getFloat(criterion.key, (BriefingEngine.DEFAULT_WEIGHTS[criterion] ?: 0.0).toFloat()).toDouble()
        }
    }

    private fun saveWeights(weights: Map<Criterion, Double>) {
        getSharedPreferences(WEIGHTS_PREFS, MODE_PRIVATE).edit().apply {
            weights.forEach { (criterion, value) -> putFloat(criterion.key, value.toFloat()) }
        }.apply()
    }

    companion object {
        const val EXTRA_BRIEFING_ID = "com.ainews.android.extra.BRIEFING_ID"
        const val EXTRA_FEED_IDS = "com.ainews.android.extra.BRIEFING_FEED_IDS"
        const val EXTRA_SCOPE_LABEL = "com.ainews.android.extra.BRIEFING_SCOPE"
        private const val PAGE_ORIGIN = "https://briefing.ainews.local/"
        private const val WEIGHTS_PREFS = "briefing-weights"

        fun openSaved(context: Context, briefingId: String): Intent =
            Intent(context, BriefingActivity::class.java)
                .putExtra(EXTRA_BRIEFING_ID, briefingId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** From a widget: a distinct data URI per widget keeps their pending intents apart. */
        fun forWidget(context: Context, appWidgetId: Int, feedSourceIds: Collection<String>, scopeLabel: String): Intent =
            Intent(context, BriefingActivity::class.java)
                .setData(Uri.parse("ainews://briefing/widget/$appWidgetId"))
                .putExtra(EXTRA_FEED_IDS, feedSourceIds.toTypedArray())
                .putExtra(EXTRA_SCOPE_LABEL, scopeLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
