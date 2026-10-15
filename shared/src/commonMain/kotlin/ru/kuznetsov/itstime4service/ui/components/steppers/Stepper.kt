package ru.kuznetsov.itstime4service.ui.components.steppers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_decrease
import itstime4service.shared.generated.resources.action_increase
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Numeric +/− stepper — default reminder window (thousand km) in settings, hours and minutes in the time picker. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    valueText: (Int) -> String = Int::toString,
) {
    val colors = ItsTime4ServiceTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            icon = ItsTime4ServiceIcons.Minus,
            contentDescription = stringResource(Res.string.action_decrease),
            enabled = value - step >= min,
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
        )
        Text(
            valueText(value),
            style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        StepButton(
            icon = ItsTime4ServiceIcons.Plus,
            contentDescription = stringResource(Res.string.action_increase),
            enabled = value + step <= max,
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
        )
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(colors.surface2)
            .border(1.dp, colors.rule, CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.inkMuted, modifier = Modifier.size(12.dp))
    }
}

@Preview
@Composable
private fun StepperLightPreview() = ComponentPreview(darkTheme = false) {
    Stepper(value = 2, onValueChange = {}, min = 1, max = 9)
}

@Preview
@Composable
private fun StepperDarkPreview() = ComponentPreview(darkTheme = true) {
    Stepper(value = 2, onValueChange = {}, min = 1, max = 9)
}
