package app.monolauncher.routine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidStepExecutorHelpersTest {
    @Test
    fun volumePercentIsClampedAndRounded() {
        assertEquals(0, volumeIndex(0, 15))
        assertEquals(8, volumeIndex(50, 15))
        assertEquals(6, volumeIndex(40, 15))
        assertEquals(15, volumeIndex(100, 15))
        assertEquals(15, volumeIndex(150, 15))
        assertEquals(0, volumeIndex(-20, 15))
    }

    @Test
    fun mediaActionsMapToMediaKeys() {
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            ),
            listOf(MediaAction.PLAY, MediaAction.PAUSE, MediaAction.TOGGLE, MediaAction.NEXT, MediaAction.PREVIOUS)
                .map { it.keyCode() },
        )
    }
}
