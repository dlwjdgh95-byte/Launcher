package app.monolauncher.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExitCountdownTest {
    private var finished = 0

    private fun TestScope.countdown() = ExitCountdown(backgroundScope) { finished++ }

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1_000L)
        runCurrent()
    }

    @Test
    fun `counts 5 to 1 and then finishes once`() = runTest {
        val countdown = countdown()
        countdown.start()
        runCurrent()

        val seen = mutableListOf(countdown.remaining.value)
        repeat(4) {
            advanceSeconds(1)
            seen += countdown.remaining.value
        }
        assertEquals(listOf(5, 4, 3, 2, 1), seen)
        assertEquals(0, finished)

        advanceSeconds(1)
        assertNull(countdown.remaining.value)
        assertEquals(1, finished)

        advanceSeconds(10)
        assertEquals(1, finished)
    }

    @Test
    fun `cancel stops without finishing`() = runTest {
        val countdown = countdown()
        countdown.start()
        runCurrent()
        advanceTimeBy(2_500)

        countdown.cancel()
        assertNull(countdown.remaining.value)

        advanceSeconds(10)
        assertNull(countdown.remaining.value)
        assertEquals(0, finished)
    }

    @Test
    fun `restart after cancel begins again from 5`() = runTest {
        val countdown = countdown()
        countdown.start()
        runCurrent()
        advanceSeconds(3)
        countdown.cancel()

        countdown.start()
        runCurrent()
        assertEquals(5, countdown.remaining.value)

        advanceSeconds(5)
        assertEquals(1, finished)
    }

    @Test
    fun `start while running does not reset the count`() = runTest {
        val countdown = countdown()
        countdown.start()
        runCurrent()
        advanceSeconds(2)

        countdown.start()
        runCurrent()
        assertEquals(3, countdown.remaining.value)

        advanceSeconds(3)
        assertEquals(1, finished)
    }

    @Test
    fun `cancel when idle is harmless`() = runTest {
        val countdown = countdown()
        countdown.cancel()
        assertNull(countdown.remaining.value)
        assertEquals(0, finished)
    }
}
