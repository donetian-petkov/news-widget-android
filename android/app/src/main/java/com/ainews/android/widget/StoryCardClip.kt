package com.ainews.android.widget

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.ainews.android.data.NewsStory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date

/**
 * Copies a story card to the clipboard for sharing, as the Mac widget does: a picture of the
 * card for apps that take images and the same text with the link for apps that take text.
 */
object StoryCardClip {
    private const val WIDTH = 1008
    private const val PADDING = 32f

    fun copy(context: Context, story: NewsStory) {
        val title = story.neutralTitle?.takeIf { it.isNotBlank() } ?: story.title
        val text = text(story, title)
        val uri = runCatching { saveCard(context, render(story, title, loadCover(story.imageUrl))) }.getOrNull()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = if (uri != null) {
            // One item with both: image-taking apps read the picture, text fields read the text.
            ClipData(
                ClipDescription(title, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN, "image/png")),
                ClipData.Item(text, null, uri),
            )
        } else {
            ClipData.newPlainText(title, text)
        }
        clipboard.setPrimaryClip(clip)
    }

    fun text(story: NewsStory, title: String): String = buildList {
        add(title)
        if (story.summary.isNotBlank()) add(story.summary)
        add("${story.source} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(story.publishedAt))}")
        if (story.sourceUrl.isNotBlank()) add(story.sourceUrl)
    }.joinToString("\n")

    private fun loadCover(url: String?): Bitmap? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
            }
            try {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** The card in its own solid light colours, so it reads the same wherever it is pasted. */
    private fun render(story: NewsStory, title: String, cover: Bitmap?): Bitmap {
        val inner = (WIDTH - 2 * PADDING).toInt()
        fun layout(text: String, size: Float, color: Int, bold: Boolean = false): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, inner)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(story.publishedAt))
        val blocks = buildList {
            add(layout("${story.source} · $date", 26f, Color.rgb(0, 107, 140), bold = true))
            add(layout(title, 38f, Color.rgb(20, 20, 20), bold = true))
            if (story.summary.isNotBlank()) add(layout(story.summary, 30f, Color.rgb(56, 56, 56)))
            Uri.parse(story.sourceUrl).host?.let { add(layout(it, 24f, Color.rgb(115, 115, 115))) }
        }
        val gap = 20f
        val coverHeight = if (cover != null) 440f else 0f
        val height = PADDING * 2 + coverHeight + (if (cover != null) gap else 0f) +
            blocks.sumOf { it.height } + gap * (blocks.size - 1)
        val bitmap = Bitmap.createBitmap(WIDTH, height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = PADDING
        if (cover != null) {
            // Cropped to fill, like the widget's own picture.
            val target = RectF(PADDING, y, WIDTH - PADDING, y + coverHeight)
            val scale = maxOf(target.width() / cover.width, target.height() / cover.height)
            val cropW = target.width() / scale
            val cropH = target.height() / scale
            val left = ((cover.width - cropW) / 2).toInt()
            val top = ((cover.height - cropH) / 2).toInt()
            canvas.drawBitmap(cover, android.graphics.Rect(left, top, (left + cropW).toInt(), (top + cropH).toInt()), target, Paint(Paint.FILTER_BITMAP_FLAG))
            y += coverHeight + gap
        }
        blocks.forEach { block ->
            canvas.save()
            canvas.translate(PADDING, y)
            block.draw(canvas)
            canvas.restore()
            y += block.height + gap
        }
        return bitmap
    }

    private fun saveCard(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.filesDir, "clips").apply { mkdirs() }
        // One file, replaced each time: the clipboard holds a single card anyway.
        val file = File(dir, "story-card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
