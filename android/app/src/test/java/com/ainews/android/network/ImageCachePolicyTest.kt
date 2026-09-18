package com.ainews.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCachePolicyTest {
    @Test
    fun fileNameForUrlIsStableAndSafe() {
        val first = ImageCachePolicy.fileNameForUrl("https://example.com/image.jpg?size=large")
        val second = ImageCachePolicy.fileNameForUrl("https://example.com/image.jpg?size=large")

        assertEquals(first, second)
        assertTrue(first.endsWith(".img"))
        assertFalse(first.contains("/"))
        assertFalse(first.contains("?"))
    }

    @Test
    fun freshnessUsesMaxAgeWindow() {
        assertTrue(ImageCachePolicy.isFresh(lastModifiedMillis = 90, nowMillis = 100, maxAgeMillis = 10))
        assertFalse(ImageCachePolicy.isFresh(lastModifiedMillis = 89, nowMillis = 100, maxAgeMillis = 10))
        assertFalse(ImageCachePolicy.isFresh(lastModifiedMillis = 0, nowMillis = 100, maxAgeMillis = 10))
    }

    @Test
    fun onlyRemoteHttpUrlsAreSupported() {
        assertTrue(ImageCachePolicy.isSupportedRemoteUrl("https://example.com/image.jpg"))
        assertTrue(ImageCachePolicy.isSupportedRemoteUrl("http://example.com/image.jpg"))
        assertFalse(ImageCachePolicy.isSupportedRemoteUrl("file:///tmp/image.jpg"))
        assertFalse(ImageCachePolicy.isSupportedRemoteUrl("content://images/1"))
    }
}
