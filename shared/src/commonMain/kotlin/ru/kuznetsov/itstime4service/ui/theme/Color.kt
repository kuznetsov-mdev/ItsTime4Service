package ru.kuznetsov.itstime4service.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral tokens — light theme
internal val BgLight = Color(0xFFEEF1F5)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val Surface2Light = Color(0xFFE3E8EE)
internal val Surface3Light = Color(0xFFCCD5DE)
internal val InkLight = Color(0xFF10151D)
internal val InkMutedLight = Color(0xFF5C6774)
internal val InkFaintLight = Color(0xFF97A2AE)
internal val RuleLight = Color(0xFFDBE1E8)

// Neutral tokens — dark theme
internal val BgDark = Color(0xFF0A0E15)
internal val SurfaceDark = Color(0xFF121823)
internal val Surface2Dark = Color(0xFF1A222F)
internal val Surface3Dark = Color(0xFF242E3D)
internal val InkDark = Color(0xFFE9EDF3)
internal val InkMutedDark = Color(0xFF8D99A8)
internal val InkFaintDark = Color(0xFF566072)
internal val RuleDark = Color(0xFF1E2733)

// Brand accent and status-tier colors — constants, identical in both themes
internal val Accent = Color(0xFF0EA5B7)
internal val OnAccent = Color(0xFFFFFFFF)
internal val TierOk = Color(0xFF1FAB8C)
internal val TierSoon = Color(0xFFE2922E)
internal val TierDue = Color(0xFFE34B3F)
internal val OnTier = Color(0xFFFFFFFF)

/**
 * Semantic color tokens for ItsTime4Service — mirrors the CSS custom properties from the design mockup.
 * Neutral tones (bg/surface/ink/rule) switch between [LightItsTime4ServiceColors]/[DarkItsTime4ServiceColors];
 * accent and tier-status colors are the same instance in both.
 */
data class ItsTime4ServiceColorScheme(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val accent: Color,
    val onAccent: Color,
    val tierOk: Color,
    val tierSoon: Color,
    val tierDue: Color,
    val onTier: Color,
)

internal val LightItsTime4ServiceColors = ItsTime4ServiceColorScheme(
    bg = BgLight,
    surface = SurfaceLight,
    surface2 = Surface2Light,
    surface3 = Surface3Light,
    ink = InkLight,
    inkMuted = InkMutedLight,
    inkFaint = InkFaintLight,
    rule = RuleLight,
    accent = Accent,
    onAccent = OnAccent,
    tierOk = TierOk,
    tierSoon = TierSoon,
    tierDue = TierDue,
    onTier = OnTier,
)

internal val DarkItsTime4ServiceColors = ItsTime4ServiceColorScheme(
    bg = BgDark,
    surface = SurfaceDark,
    surface2 = Surface2Dark,
    surface3 = Surface3Dark,
    ink = InkDark,
    inkMuted = InkMutedDark,
    inkFaint = InkFaintDark,
    rule = RuleDark,
    accent = Accent,
    onAccent = OnAccent,
    tierOk = TierOk,
    tierSoon = TierSoon,
    tierDue = TierDue,
    onTier = OnTier,
)
