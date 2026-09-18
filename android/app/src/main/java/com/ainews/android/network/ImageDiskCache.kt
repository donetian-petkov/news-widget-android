package com.ainews.android.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
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

    suspend fun loadBitmap(context: Context, imageUrl: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = getOrFetch(context, imageUrl) ?: return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        }

    suspend fun prefetch(context: Context, imageUrls: List<String>) {
        withContext(Dispatchers.IO) {
            imageUrls.distinct().take(PREFETCH_LIMIT).forEach { imageUrl ->
                runCatching { getOrFetch(context, imageUrl) }
            }
        }
    }

    private fun getOrFetch(context: Context, imageUrl: String): File? {
        if (!ImageCachePolicy.isSupportedRemoteUrl(imageUrl)) return null
        val target = File(cacheDir(context), ImageCachePolicy.fileNameForUrl(imageUrl))
        cachedFile(target)?.let { return it }

        val temp = File(target.parentFile, "${target.name}.tmp")
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AI-News-Android/0.1")
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
        }.getOrElse {
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

    private companion object {
        const val CACHE_DIR_NAME = "story-images"
        const val PREFETCH_LIMIT = 12
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
