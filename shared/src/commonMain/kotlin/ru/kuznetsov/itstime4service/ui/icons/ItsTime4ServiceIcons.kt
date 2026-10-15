package ru.kuznetsov.itstime4service.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Filter
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wrench

/**
 * The 17 icons used across ItsTime4Service's screens, mapped to [Lucide] — the icon set the design
 * mockup's own custom icons were modeled on (same 24x24 grid, round linecap/linejoin). Screens
 * and ui-kit components reference [ItsTime4ServiceIcons.*] rather than `Lucide.*` directly, so the icon
 * source could be swapped later without touching call sites.
 */
object ItsTime4ServiceIcons {
    val Car: ImageVector get() = Lucide.Car
    val Gauge: ImageVector get() = Lucide.Gauge
    val Droplet: ImageVector get() = Lucide.Droplet
    val Disc: ImageVector get() = Lucide.Disc
    val Filter: ImageVector get() = Lucide.Filter
    val Wrench: ImageVector get() = Lucide.Wrench
    val Gear: ImageVector get() = Lucide.Settings
    val Clock: ImageVector get() = Lucide.Clock
    val Check: ImageVector get() = Lucide.Check
    val ChevronLeft: ImageVector get() = Lucide.ChevronLeft
    val ChevronDown: ImageVector get() = Lucide.ChevronDown
    val Pencil: ImageVector get() = Lucide.Pencil
    val Trash: ImageVector get() = Lucide.Trash2
    val Bell: ImageVector get() = Lucide.Bell
    val Plus: ImageVector get() = Lucide.Plus
    val Minus: ImageVector get() = Lucide.Minus
    val Sun: ImageVector get() = Lucide.Sun
    val MoonToggle: ImageVector get() = Lucide.Moon
}