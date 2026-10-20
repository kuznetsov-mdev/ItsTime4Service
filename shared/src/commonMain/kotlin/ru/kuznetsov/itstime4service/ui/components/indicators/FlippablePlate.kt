package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview

/**
 * A licence plate that turns over around its horizontal axis to hide or show the number: the face is [look] as it is,
 * the back is the same plate with blurred placeholders (see [LicensePlate] with `masked`). When [hidden] changes the
 * plate turns over in 500 ms; it is shown on the right side at once when it first appears, in previews and with the
 * system's reduce-motion setting on.
 */
@Composable
fun FlippablePlate(look: PlateLook, hidden: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val target = if (hidden) 180f else 0f
    val still = LocalInspectionMode.current || ReduceMotion.isEnabled()
    val rotation = remember { Animatable(target) }
    LaunchedEffect(target, still) {
        if (still) rotation.snapTo(target) else rotation.animateTo(target, tween(FLIP_MS, easing = FastOutSlowInEasing))
    }
    val distance = with(LocalDensity.current) { CAMERA_DISTANCE.dp.toPx() }
    val showsBack = rotation.value > 90f
    Box(
        modifier = modifier.graphicsLayer {
            rotationX = rotation.value
            cameraDistance = distance
        },
        contentAlignment = Alignment.Center,
    ) {
        if (showsBack) {
            LicensePlate(look, Modifier.graphicsLayer { rotationX = 180f }, compact = compact, masked = true)
        } else {
            LicensePlate(look, compact = compact)
        }
    }
}

private const val FLIP_MS = 500
private const val CAMERA_DISTANCE = 96

@Composable
private fun FlippablePlatePreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlippablePlate(PlateLook.Russian("А 123 ВС", "77", "А123ВС77"), hidden = false)
        FlippablePlate(PlateLook.Russian("А 123 ВС", "77", "А123ВС77"), hidden = true)
    }
}

@Preview
@Composable
private fun FlippablePlateLightPreview() = ComponentPreview(darkTheme = false) {
    FlippablePlatePreviewContent()
}

@Preview
@Composable
private fun FlippablePlateDarkPreview() = ComponentPreview(darkTheme = true) {
    FlippablePlatePreviewContent()
}
