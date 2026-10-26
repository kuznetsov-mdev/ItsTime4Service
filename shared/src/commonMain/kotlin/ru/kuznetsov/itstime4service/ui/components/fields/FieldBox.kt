package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_done
import itstime4service.shared.generated.resources.field_required
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Shared chrome (surface2 fill, 1dp rule border — red when the field is in error — and placeholder handling) behind every field
 * component — [ru.kuznetsov.itstime4service.ui.components.fields.AppTextField],
 * [ru.kuznetsov.itstime4service.ui.components.fields.AppNumberField], [LabeledMiniField] and
 * [InlineEditField] all draw through this so the box styling stays in one place.
 * Where the numeric keyboard has no done key ([NumericKeyboard]), a focused numeric field shows a small check button
 * at its end: it does what the done key does — [onImeAction], or, without one, just closes the keyboard. The button
 * takes the whole height of the field and 44 dp of its width to be easy to hit; the field keeps its height.
 * [showDoneButton] turns it off for a field that has its own button.
 * A field with [isError] gets a red outline and tells the screen reader "Required field".
 */
@Composable
internal fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    showDoneButton: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = ItsTime4ServiceTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val needsDoneButton = showDoneButton && !NumericKeyboard.hasDoneKey && keyboardType == KeyboardType.Number
    val showsDoneButton = needsDoneButton && isFocused
    val layoutDirection = LocalLayoutDirection.current
    val errorText = stringResource(Res.string.field_required)
    var edited by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (edited.text == value) edited else TextFieldValue(value, TextRange(value.length))
    Row(
        modifier = modifier
            .then(if (needsDoneButton) Modifier.height(IntrinsicSize.Min) else Modifier)
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, if (isError) colors.tierDue else colors.rule, shape),
        verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = if (showsDoneButton) 0.dp else contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle, color = colors.inkFaint)
                }
                BasicTextField(
                    value = shown,
                    onValueChange = {
                        edited = it
                        if (it.text != value) onValueChange(it.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { isFocused = it.isFocused }
                        .semantics { if (isError) error(errorText) },
                    enabled = enabled,
                    textStyle = textStyle.copy(color = colors.ink),
                    singleLine = minLines == 1,
                    minLines = minLines,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                    visualTransformation = visualTransformation,
                )
            }
            if (trailing != null) trailing()
        }
        if (showsDoneButton) {
            Box(
                modifier = Modifier
                    .width(DONE_TARGET_WIDTH)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.action_done)) {
                        if (onImeAction != null) onImeAction() else focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusS))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ItsTime4ServiceIcons.Check,
                        contentDescription = stringResource(Res.string.action_done),
                        tint = colors.onAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

private val DONE_TARGET_WIDTH = 44.dp
