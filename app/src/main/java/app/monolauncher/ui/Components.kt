package app.monolauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BasicTextMenuKeys = setOf(
    TextContextMenuKeys.CutKey,
    TextContextMenuKeys.CopyKey,
    TextContextMenuKeys.PasteKey,
    TextContextMenuKeys.SelectAllKey,
)

/** True for cut, copy, paste and select all; false for process-text, smart-selection, autofill and other items. */
internal fun isBasicTextMenuItem(component: TextContextMenuComponent): Boolean = component.key in BasicTextMenuKeys

/**
 * Limits the text selection menu to cut/copy/paste/select all. The other items (translate, "process text",
 * smart-selection actions) open other apps, which would bypass the registered-app limit during a session.
 */
fun Modifier.basicTextMenuOnly(): Modifier = filterTextContextMenuComponents(::isBasicTextMenuItem)

/** Plain text input: no capitalisation or autocorrect, which would garble Hangul typed in English mode. */
val PlainKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
)

@Composable
fun MonoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Mono.Text,
    fontSize: TextUnit = 16.sp,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = fontSize)
    }
}

@Composable
fun MonoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Mono.Muted),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mono.Text),
    ) {
        Text(text, fontSize = fontSize)
    }
}

/** Underlined input box with a gray placeholder, shared by every text field. */
@Composable
fun FieldDecoration(isEmpty: Boolean, placeholder: String, textStyle: TextStyle, innerTextField: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Mono.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 12.dp),
    ) {
        if (isEmpty) Text(placeholder, style = textStyle.copy(color = Mono.Dim))
        innerTextField()
    }
}

@Composable
fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = PlainKeyboard,
) {
    val style = textStyle.copy(color = Mono.Text)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(Mono.Text),
        decorationBox = { inner -> FieldDecoration(value.isEmpty(), placeholder, style, inner) },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Mono.Muted, style = MaterialTheme.typography.labelLarge)
}
