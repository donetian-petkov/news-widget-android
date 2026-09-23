package com.ainews.android.network

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTextCleanerTest {

    @Test
    fun stripsTagsAndCollapsesSpace() {
        assertEquals(
            "Radev bans gambling advertising",
            cleanFeedText("<p>Radev bans   <b>gambling</b>\nadvertising</p>"),
        )
    }

    @Test
    fun dropsEditingSystemPlaceholders() {
        assertEquals(
            "A protest outside parliament.",
            cleanFeedText("[[gallery]] A protest [[img:4956908]] outside parliament."),
        )
    }

    @Test
    fun decodesCharacterCodes() {
        assertEquals(
            "Dnevnik's “quote” – and the rest…",
            cleanFeedText("Dnevnik&#039;s &#8220;quote&#8221; &ndash; and the rest&#8230;"),
        )
    }

    @Test
    fun decodesHexCodesAndAmpersands() {
        assertEquals("Marks & Spencer «2026»", cleanFeedText("Marks &amp; Spencer &#xAB;2026&#xBB;"))
    }

    @Test
    fun decodesOnlyOnce() {
        assertEquals("&lt;b&gt;", cleanFeedText("&amp;lt;b&amp;gt;"))
    }

    @Test
    fun finishesTextThatWasEncodedTwice() {
        assertEquals("1 October \u2013 music day\u2026", cleanFeedText("1 October &amp;ndash; music day&amp;#8230;"))
    }

    @Test
    fun leavesUnknownCodesAlone() {
        assertEquals("100 &fakecode; done", cleanFeedText("100 &fakecode; done"))
    }

    @Test
    fun keepsOrdinarySquareBrackets() {
        assertEquals("The report [see page 4] is out", cleanFeedText("The report [see page 4] is out"))
    }
}
