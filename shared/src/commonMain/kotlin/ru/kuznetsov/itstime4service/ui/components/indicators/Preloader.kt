package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.loading
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * What a screen shows while its data is loading: a car rushing inside a circle — an accent arc runs along the rim,
 * speed lines dash behind the car, which bobs slightly — with "Loading…" below, centred in [modifier].
 * Local data usually comes in a moment, so the preloader appears only when loading lasts longer than [delayMillis];
 * until then the space stays empty. In previews it is shown at once; with the system's reduce-motion setting on it
 * stands still.
 */
@Composable
fun Preloader(modifier: Modifier = Modifier, delayMillis: Long = DELAY_MS) {
    val inPreview = LocalInspectionMode.current
    var visible by remember { mutableStateOf(inPreview) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    if (!visible) {
        Spacer(modifier)
        return
    }
    val colors = ItsTime4ServiceTheme.colors
    val label = stringResource(Res.string.loading)
    val still = inPreview || ReduceMotion.isEnabled()
    val transition = if (still) null else rememberInfiniteTransition()
    val spin = transition?.animateFloat(0f, 360f, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)))?.value ?: 0f
    val bob = transition?.animateFloat(-1f, 1f, infiniteRepeatable(tween(BOB_MS), RepeatMode.Reverse))?.value ?: 0f
    val dash = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(DASH_MS, easing = LinearEasing)))?.value ?: 0f
    Box(modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.rule, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val inset = 3.dp.toPx() + stroke / 2
                    drawArc(
                        colors.accent,
                        startAngle = (if (still) 0f else spin) - 90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                SPEED_LINES.forEachIndexed { index, line ->
                    val phase = if (still) 0.5f else (dash + index / SPEED_LINES.size.toFloat()) % 1f
                    Box(
                        modifier = Modifier
                            .offset(x = (-24 + 18 * (1 - phase) - 9).dp, y = line.y.dp)
                            .width(line.width.dp)
                            .height(2.dp)
                            .graphicsLayer { alpha = if (still) 0.6f else 0.9f * sin(phase * PI.toFloat()) }
                            .clip(RoundedCornerShape(1.dp))
                            .background(colors.inkFaint),
                    )
                }
                Icon(
                    ItsTime4ServiceIcons.Car,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.offset(x = 4.dp, y = (if (still) 0f else bob).dp).size(34.dp),
                )
            }
            Text(label, style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 12.sp), color = colors.inkFaint)
        }
    }
}

private class SpeedLine(val y: Int, val width: Int)

private val SPEED_LINES = listOf(SpeedLine(-6, 12), SpeedLine(0, 18), SpeedLine(6, 10))

private const val DELAY_MS = 300L
private const val SPIN_MS = 1100
private const val BOB_MS = 320
private const val DASH_MS = 600

@Preview
@Composable
private fun PreloaderLightPreview() = ComponentPreview(darkTheme = false) {
    Preloader(Modifier.size(200.dp))
}

@Preview
@Composable
private fun PreloaderDarkPreview() = ComponentPreview(darkTheme = true) {
    Preloader(Modifier.size(200.dp))
}
