package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlCodecTest {
    @Test
    fun readsFeedsFromOpml() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Example" xmlUrl="https://example.com/rss" />
                <outline title="Second" xmlUrl="https://second.example/feed" />
                <outline text="Folder" />
              </body>
            </opml>
        """.trimIndent()

        val feeds = OpmlCodec.parse(opml)

        assertEquals(2, feeds.size)
        assertEquals("Example", feeds.first().title)
        assertEquals("https://second.example/feed", feeds.last().url)
    }

    @Test
    fun writesFeedsBackAsOpml() {
        val written = OpmlCodec.write(
            listOf(FeedSource(id = "a", title = "A & B", url = "https://a.example/rss")),
        )

        assertTrue(written.contains("xmlUrl=\"https://a.example/rss\""))
        assertTrue(written.contains("A &amp; B"))
        assertEquals(1, OpmlCodec.parse(written).size)
    }

    @Test
    fun brokenInputReturnsNoFeeds() {
        assertTrue(OpmlCodec.parse("not opml at all").isEmpty())
    }
}
