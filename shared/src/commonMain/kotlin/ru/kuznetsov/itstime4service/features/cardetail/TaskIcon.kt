package ru.kuznetsov.itstime4service.features.cardetail

import androidx.compose.ui.graphics.vector.ImageVector
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons

private val oilKeywords = listOf("масл", "oil", "aceite")
private val brakeKeywords = listOf("колод", "тормоз", "brake", "pad", "freno", "pastilla")
private val filterKeywords = listOf("фильтр", "filter", "filtro")

internal fun taskIcon(taskName: String): ImageVector {
    val name = taskName.lowercase()
    return when {
        oilKeywords.any { it in name } -> ItsTime4ServiceIcons.Droplet
        brakeKeywords.any { it in name } -> ItsTime4ServiceIcons.Disc
        filterKeywords.any { it in name } -> ItsTime4ServiceIcons.Filter
        else -> ItsTime4ServiceIcons.Wrench
    }
}
