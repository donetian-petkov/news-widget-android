package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A port of the Mac widget's summaryText.test.ts. */
class SummaryTextTest {
    @Test
    fun dropsASentenceTheModelCutOffAndJoinsItsLines() {
        assertEquals(
            "Полицията задържа 21-годишен мъж. Разследването продължава.",
            SummaryText.cleanSummary("Полицията задържа 21-годишен мъж.  \nРазследването продължава.  \n\nЗадържаният е бил"),
        )
    }

    @Test
    fun keepsOneSentenceAndTheNextOnlyWhileTheWholeStaysShort() {
        val first = "Сушата съсипва поминъка в афганистанската провинция Бадгис и семействата продават дъщерите си, за да погасят дълговете."
        assertEquals("$first Хатира е сгодена на 11 години.", SummaryText.cleanSummary("$first Хатира е сгодена на 11 години. Тя е бременна на 14."))
        assertEquals(first, SummaryText.cleanSummary("$first $first"))
        assertEquals("Летищата в Рим затвориха. Полетите са отменени.", SummaryText.cleanSummary("Летищата в Рим затвориха. Полетите са отменени."))
    }

    @Test
    fun treatsAnyMentionOfNoSummaryAsNoSummary() {
        assertEquals("", SummaryText.cleanSummary("Няма наличен допълнителен факт в контекста. NO_SUMMARY"))
        assertEquals("", SummaryText.cleanSummary("NO_SUMMARY."))
        assertEquals("", SummaryText.cleanSummary(null))
    }

    @Test
    fun cleansEachHalfOfABilingualSummary() {
        assertEquals("Летищата затвориха. / Airports closed.", SummaryText.cleanSummary("Летищата затвориха. Полетите / Airports closed. Flights were"))
    }

    @Test
    fun leavesASingleSentenceWithoutAFullStopAlone() {
        assertEquals("Радев призова ЕС да засили охраната", SummaryText.wholeSentences("Радев призова ЕС да засили охраната"))
    }
}
