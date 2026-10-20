package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_save
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Compact field + accent confirm button — in-place edit of the car name, VIN and mileage on the car screen; takes focus as it appears, saves on the button or on the keyboard's done key, and neither saves while [canSave] rejects the text. */
@Composable
fun InlineEditField(
    initialValue: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    textStyle: TextStyle = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 13.sp),
    placeholder: String? = null,
    transform: (String) -> String = { it },
    canSave: (String) -> Boolean = { true },
) {
    val colors = ItsTime4ServiceTheme.colors
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val saveEnabled = canSave(text)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldBox(
            value = text,
            onValueChange = { text = transform(it) },
            textStyle = textStyle,
            shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusS),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            keyboardType = keyboardType,
            focusRequester = focusRequester,
            imeAction = ImeAction.Done,
            onImeAction = { if (canSave(text)) onSave(text) },
            showDoneButton = false,
        )
        Box(
            modifier = Modifier
                .size(27.dp)
                .alpha(if (saveEnabled) 1f else 0.35f)
                .clip(RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusS))
                .background(colors.accent)
                .clickable(enabled = saveEnabled) { onSave(text) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(ItsTime4ServiceIcons.Check, contentDescription = stringResource(Res.string.action_save), tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
    }
}

@Preview
@Composable
private fun InlineEditFieldLightPreview() = ComponentPreview(darkTheme = false) {
    InlineEditField(initialValue = "84300", onSave = {})
}

@Preview
@Composable
private fun InlineEditFieldDarkPreview() = ComponentPreview(darkTheme = true) {
    InlineEditField(initialValue = "84300", onSave = {})
}
