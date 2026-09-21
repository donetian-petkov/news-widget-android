package com.ainews.android.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ImageDiskCache(
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    fun loadCachedBitmap(context: Context, imageUrl: String): Bitmap? {
        val file = cachedFile(context, imageUrl) ?: return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Widgets travel as RemoteViews with a hard bitmap budget (about 15 MB for the whole
     * update), so widget images are decoded down to thumbnail size rather than full size.
     */
    fun loadCachedThumbnail(context: Context, imageUrl: String, maxSizePx: Int = THUMBNAIL_MAX_PX): Bitmap? {
        val file = cachedFile(context, imageUrl) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (largestSide <= 0) return null

        var sampleSize = 1
        while (largestSide / (sampleSize * 2) >= maxSizePx) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** Cached thumbnail if there is one, otherwise downloads it first. */
    suspend fun loadOrFetchThumbnail(
        context: Context,
        imageUrl: String,
        maxSizePx: Int = THUMBNAIL_MAX_PX,
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadCachedThumbnail(context, imageUrl, maxSizePx)
            ?: run {
                getOrFetch(context, imageUrl) ?: return@run null
                loadCachedThumbnail(context, imageUrl, maxSizePx)
            }
    }

    suspend fun loadBitmap(context: Context, imageUrl: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = getOrFetch(context, imageUrl) ?: return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        }

    suspend fun prefetch(context: Context, imageUrls: List<String>) {
        coroutineScope {
            val gate = Semaphore(PREFETCH_PARALLELISM)
            imageUrls.distinct().take(PREFETCH_LIMIT).map { imageUrl ->
                async(Dispatchers.IO) {
                    gate.withPermit { runCatching { getOrFetch(context, imageUrl) } }
                }
            }.awaitAll()
        }
    }

    private fun getOrFetch(context: Context, rawImageUrl: String): File? {
        if (!ImageCachePolicy.isSupportedRemoteUrl(rawImageUrl)) return null
        // Android blocks plain HTTP, and several feeds still publish http image links,
        // so ask the same host over https instead of losing the picture.
        val imageUrl = if (rawImageUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + rawImageUrl.removePrefix("http://")
        } else {
            rawImageUrl
        }
        val target = File(cacheDir(context), ImageCachePolicy.fileNameForUrl(rawImageUrl))
        cachedFile(target)?.let { return it }

        val temp = File(target.parentFile, "${target.name}.tmp")
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            // News CDNs routinely refuse unknown clients, which is why some thumbnails
            // never arrived while the same URL loads fine in a browser.
            setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            runCatching { URL(imageUrl) }.getOrNull()?.let { url ->
                setRequestProperty("Referer", "${url.protocol}://${url.host}/")
            }
        }

        return runCatching {
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > maxBytes) error("Image too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (temp.length() == 0L) return@runCatching null
            if (target.exists()) target.delete()
            temp.renameTo(target)
            target
        }.getOrElse { failure ->
            android.util.Log.w("AiNewsRefresh", "image failed $imageUrl: $failure")
            temp.delete()
            target.takeIf { existing -> existing.exists() }
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    private fun cachedFile(context: Context, imageUrl: String): File? {
        if (!ImageCachePolicy.isSupportedRemoteUrl(imageUrl)) return null
        return cachedFile(File(cacheDir(context), ImageCachePolicy.fileNameForUrl(imageUrl)))
    }

    private fun cachedFile(target: File): File? =
        target.takeIf {
            it.exists() && ImageCachePolicy.isFresh(it.lastModified(), nowMillis(), maxAgeMillis)
        }

    private fun nowMillis(): Long = System.currentTimeMillis()

    companion object {
        const val CACHE_DIR_NAME = "story-images"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
        const val PREFETCH_LIMIT = 60
        const val PREFETCH_PARALLELISM = 6
        const val THUMBNAIL_MAX_PX = 88
        const val DEFAULT_MAX_BYTES = 5L * 1024L * 1024L
        const val DEFAULT_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}

object ImageCachePolicy {
    fun fileNameForUrl(imageUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(imageUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$digest.img"
    }

    fun isFresh(lastModifiedMillis: Long, nowMillis: Long, maxAgeMillis: Long): Boolean =
        lastModifiedMillis > 0 && nowMillis - lastModifiedMillis <= maxAgeMillis

    fun isSupportedRemoteUrl(imageUrl: String): Boolean =
        imageUrl.startsWith("https://", ignoreCase = true) ||
            imageUrl.startsWith("http://", ignoreCase = true)
}
