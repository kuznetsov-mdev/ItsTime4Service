package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * A checkbox with its [label]; the whole row toggles. Checked — an accent square with a check mark,
 * unchecked — an empty square with a rule border.
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(shape)
                .background(if (checked) colors.accent else colors.surface2)
                .border(1.5.dp, if (checked) colors.accent else colors.rule, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(ItsTime4ServiceIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = colors.ink,
        )
    }
}

@Composable
private fun AppCheckboxPreviewContent() {
    Column {
        AppCheckbox(checked = false, onCheckedChange = {}, label = "Create a history record")
        AppCheckbox(checked = true, onCheckedChange = {}, label = "Create a history record")
    }
}

@Preview
@Composable
private fun AppCheckboxLightPreview() = ComponentPreview(darkTheme = false) {
    AppCheckboxPreviewContent()
}

@Preview
@Composable
private fun AppCheckboxDarkPreview() = ComponentPreview(darkTheme = true) {
    AppCheckboxPreviewContent()
}
