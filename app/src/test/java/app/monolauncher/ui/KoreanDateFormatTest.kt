package app.monolauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class KoreanDateFormatTest {
    @Test
    fun `short date is month day weekday`() {
        assertEquals("10월 8일 목", KoreanDateFormat.short(LocalDate.of(2026, 10, 8)))
        assertEquals("10월 9일 금", KoreanDateFormat.short(LocalDate.of(2026, 10, 9)))
        assertEquals("1월 1일 목", KoreanDateFormat.short(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `weekdays map Monday to Sunday`() {
        // 2026-10-05 is a Monday.
        val week = (0L..6L).map { KoreanDateFormat.weekday(LocalDate.of(2026, 10, 5).plusDays(it)) }
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), week)
    }

    @Test
    fun `long date has year without weekday`() {
        assertEquals("2026년 10월 8일", KoreanDateFormat.long(LocalDate.of(2026, 10, 8)))
        assertEquals("2027년 12월 31일", KoreanDateFormat.long(LocalDate.of(2027, 12, 31)))
    }

    @Test
    fun `time is zero padded 24 hour`() {
        assertEquals("09:05", KoreanDateFormat.time(LocalTime.of(9, 5)))
        assertEquals("00:00", KoreanDateFormat.time(LocalTime.MIDNIGHT))
        assertEquals("23:59", KoreanDateFormat.time(LocalTime.of(23, 59, 59)))
    }

    @Test
    fun `next tick lands just after the minute boundary`() {
        assertEquals(60_020L, KoreanDateFormat.millisUntilNextMinute(LocalTime.of(14, 32, 0)))
        assertEquals(30_020L, KoreanDateFormat.millisUntilNextMinute(LocalTime.of(14, 32, 30)))
        assertEquals(520L, KoreanDateFormat.millisUntilNextMinute(LocalTime.of(14, 32, 59, 500_000_000)))
    }
}
