package app.monolauncher.ui

import java.time.LocalDate
import java.time.LocalTime

/** Locale-independent Korean clock/date strings for the home header. */
object KoreanDateFormat {
    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** "14:05" */
    fun time(time: LocalTime): String = "${twoDigits(time.hour)}:${twoDigits(time.minute)}"

    /** "10월 8일 목" */
    fun short(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date)}"

    /** "2026년 10월 8일" */
    fun long(date: LocalDate): String = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

    /** "목" */
    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** Delay until just after the next minute boundary, so a ticking clock never shows a stale minute. */
    fun millisUntilNextMinute(time: LocalTime): Long {
        val intoMinute = time.second * 1_000L + time.nano / 1_000_000L
        return 60_000L - intoMinute + 20L
    }

    private fun twoDigits(n: Int) = n.toString().padStart(2, '0')
}
