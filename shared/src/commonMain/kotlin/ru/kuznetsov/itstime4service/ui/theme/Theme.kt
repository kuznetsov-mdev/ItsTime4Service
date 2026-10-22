package ru.kuznetsov.itstime4service.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalItsTime4ServiceColors = staticCompositionLocalOf { LightItsTime4ServiceColors }
private val LocalItsTime4ServiceTypography = staticCompositionLocalOf { ItsTime4ServiceTypography() }
private val LocalItsTime4ServiceDimens = staticCompositionLocalOf { ItsTime4ServiceDimens() }

/** Access point for ItsTime4Service's design tokens, mirrored after `MaterialTheme.colorScheme` etc. */
object ItsTime4ServiceTheme {
    val colors: ItsTime4ServiceColorScheme
        @Composable get() = LocalItsTime4ServiceColors.current

    val typography: ItsTime4ServiceTypography
        @Composable get() = LocalItsTime4ServiceTypography.current

    val dimens: ItsTime4ServiceDimens
        @Composable get() = LocalItsTime4ServiceDimens.current
}

/**
 * Root theme wrapper. Follows the system theme by default; the app passes `darkTheme` from the theme chosen
 * in settings.
 */
@Composable
fun ItsTime4ServiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val itsTime4ServiceColors = if (darkTheme) DarkItsTime4ServiceColors else LightItsTime4ServiceColors
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = itsTime4ServiceColors.accent,
            onPrimary = itsTime4ServiceColors.onAccent,
            background = itsTime4ServiceColors.bg,
            onBackground = itsTime4ServiceColors.ink,
            surface = itsTime4ServiceColors.surface,
            onSurface = itsTime4ServiceColors.ink,
            surfaceVariant = itsTime4ServiceColors.surface2,
            outline = itsTime4ServiceColors.rule,
        )
    } else {
        lightColorScheme(
            primary = itsTime4ServiceColors.accent,
            onPrimary = itsTime4ServiceColors.onAccent,
            background = itsTime4ServiceColors.bg,
            onBackground = itsTime4ServiceColors.ink,
            surface = itsTime4ServiceColors.surface,
            onSurface = itsTime4ServiceColors.ink,
            surfaceVariant = itsTime4ServiceColors.surface2,
            outline = itsTime4ServiceColors.rule,
        )
    }

    CompositionLocalProvider(
        LocalItsTime4ServiceColors provides itsTime4ServiceColors,
        LocalItsTime4ServiceTypography provides ItsTime4ServiceTypography(),
        LocalItsTime4ServiceDimens provides ItsTime4ServiceDimens(),
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content,
        )
    }
}