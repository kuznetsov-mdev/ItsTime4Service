package ru.kuznetsov.itstime4service.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Battery
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Filter
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.LayoutPanelTop
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.PanelTop
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Wind
import com.composables.icons.lucide.Wrench
import com.composables.icons.lucide.Zap

/**
 * The icons used across ItsTime4Service's screens, mapped to [Lucide] — the icon set the design
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
    val ChevronRight: ImageVector get() = Lucide.ChevronRight
    val ChevronDown: ImageVector get() = Lucide.ChevronDown
    val Pencil: ImageVector get() = Lucide.Pencil
    val Trash: ImageVector get() = Lucide.Trash2
    val Bell: ImageVector get() = Lucide.Bell
    val Plus: ImageVector get() = Lucide.Plus
    val Minus: ImageVector get() = Lucide.Minus
    val Sun: ImageVector get() = Lucide.Sun
    val MoonToggle: ImageVector get() = Lucide.Moon
    val List: ImageVector get() = Lucide.List
    val PanelTop: ImageVector get() = Lucide.PanelTop
    val LayoutPanelTop: ImageVector get() = Lucide.LayoutPanelTop
    val Zap: ImageVector get() = Lucide.Zap
    val Battery: ImageVector get() = Lucide.Battery
    val Thermometer: ImageVector get() = Lucide.Thermometer
    val Tire: ImageVector get() = Lucide.CircleDot
    val Wind: ImageVector get() = Lucide.Wind
    val ScrollText: ImageVector get() = Lucide.ScrollText
}