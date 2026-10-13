package ru.kuznetsov.pitstop.ui.components.fields

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** Upper-case caption above a form field; a required field gets a red asterisk. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false, bottomPadding: Boolean = true) {
    val colors = PitStopTheme.colors
    Text(
        buildAnnotatedString {
            append(text.uppercase())
            if (required) {
                withStyle(SpanStyle(color = colors.tierDue)) { append(" *") }
            }
        },
        style = PitStopTheme.typography.sans.copy(
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04f.em,
        ),
        color = colors.inkMuted,
        modifier = modifier.padding(bottom = if (bottomPadding) 8.dp else 0.dp),
    )
}
