package app.monolauncher.ui

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMenuFilterTest {
    private fun item(key: Any) = TextContextMenuItem(key = key, label = key.toString(), leadingIcon = 0, onClick = {})

    @Test
    fun `only cut, copy, paste and select all are kept`() {
        listOf(
            TextContextMenuKeys.CutKey,
            TextContextMenuKeys.CopyKey,
            TextContextMenuKeys.PasteKey,
            TextContextMenuKeys.SelectAllKey,
        ).forEach { assertTrue(isBasicTextMenuItem(item(it))) }

        // "Process text" (translate etc.), smart-selection actions and autofill can open other apps;
        // their keys are not public, so any other key stands in for them.
        assertFalse(isBasicTextMenuItem(item(TextContextMenuKeys.AutofillKey)))
        assertFalse(isBasicTextMenuItem(item("process-text")))
        assertFalse(isBasicTextMenuItem(item(Any())))
    }
}
