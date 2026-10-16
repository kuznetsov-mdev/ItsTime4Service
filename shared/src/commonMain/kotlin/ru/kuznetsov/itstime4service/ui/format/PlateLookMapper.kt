package ru.kuznetsov.itstime4service.ui.format

import ru.kuznetsov.itstime4service.domain.model.PlateStyle
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook

fun PlateStyle.toLook(plate: String): PlateLook = when (this) {
    is PlateStyle.Russian -> PlateLook.Russian(series = series, region = region, text = plate)
    is PlateStyle.European -> PlateLook.European(countryCode = countryCode, text = plate)
    PlateStyle.American -> PlateLook.American(text = plate)
    PlateStyle.Universal -> PlateLook.Universal(text = plate)
}
