package ru.kuznetsov.itstime4service.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.task_icon_battery
import itstime4service.shared.generated.resources.task_icon_car
import itstime4service.shared.generated.resources.task_icon_disc
import itstime4service.shared.generated.resources.task_icon_droplet
import itstime4service.shared.generated.resources.task_icon_filter
import itstime4service.shared.generated.resources.task_icon_gauge
import itstime4service.shared.generated.resources.task_icon_gear
import itstime4service.shared.generated.resources.task_icon_thermometer
import itstime4service.shared.generated.resources.task_icon_tire
import itstime4service.shared.generated.resources.task_icon_wind
import itstime4service.shared.generated.resources.task_icon_wrench
import itstime4service.shared.generated.resources.task_icon_zap
import org.jetbrains.compose.resources.StringResource
import ru.kuznetsov.itstime4service.domain.model.TaskIcon

private val oilKeywords = listOf("масл", "oil", "aceite")
private val brakeKeywords = listOf("колод", "тормоз", "brake", "pad", "freno", "pastilla")
private val filterKeywords = listOf("фильтр", "filter", "filtro")

internal fun taskIcon(icon: TaskIcon?, taskName: String): TaskIcon {
    if (icon != null) return icon
    val name = taskName.lowercase()
    return when {
        oilKeywords.any { it in name } -> TaskIcon.DROPLET
        brakeKeywords.any { it in name } -> TaskIcon.DISC
        filterKeywords.any { it in name } -> TaskIcon.FILTER
        else -> TaskIcon.WRENCH
    }
}

internal val TaskIcon.vector: ImageVector
    get() = when (this) {
        TaskIcon.WRENCH -> ItsTime4ServiceIcons.Wrench
        TaskIcon.DROPLET -> ItsTime4ServiceIcons.Droplet
        TaskIcon.DISC -> ItsTime4ServiceIcons.Disc
        TaskIcon.FILTER -> ItsTime4ServiceIcons.Filter
        TaskIcon.ZAP -> ItsTime4ServiceIcons.Zap
        TaskIcon.BATTERY -> ItsTime4ServiceIcons.Battery
        TaskIcon.THERMOMETER -> ItsTime4ServiceIcons.Thermometer
        TaskIcon.TIRE -> ItsTime4ServiceIcons.Tire
        TaskIcon.WIND -> ItsTime4ServiceIcons.Wind
        TaskIcon.GEAR -> ItsTime4ServiceIcons.Gear
        TaskIcon.GAUGE -> ItsTime4ServiceIcons.Gauge
        TaskIcon.CAR -> ItsTime4ServiceIcons.Car
    }

internal val TaskIcon.label: StringResource
    get() = when (this) {
        TaskIcon.WRENCH -> Res.string.task_icon_wrench
        TaskIcon.DROPLET -> Res.string.task_icon_droplet
        TaskIcon.DISC -> Res.string.task_icon_disc
        TaskIcon.FILTER -> Res.string.task_icon_filter
        TaskIcon.ZAP -> Res.string.task_icon_zap
        TaskIcon.BATTERY -> Res.string.task_icon_battery
        TaskIcon.THERMOMETER -> Res.string.task_icon_thermometer
        TaskIcon.TIRE -> Res.string.task_icon_tire
        TaskIcon.WIND -> Res.string.task_icon_wind
        TaskIcon.GEAR -> Res.string.task_icon_gear
        TaskIcon.GAUGE -> Res.string.task_icon_gauge
        TaskIcon.CAR -> Res.string.task_icon_car
    }
