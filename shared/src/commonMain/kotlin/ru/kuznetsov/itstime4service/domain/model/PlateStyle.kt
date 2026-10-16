package ru.kuznetsov.itstime4service.domain.model

/**
 * The kind of licence plate a typed plate number is shown as.
 * [Russian] carries the series ("А 123 ВС") and the region ("77") split out of the number;
 * [European] carries the country code printed on the blue band ("E", "D", "F"…).
 */
sealed interface PlateStyle {
    data class Russian(val series: String, val region: String) : PlateStyle

    data class European(val countryCode: String) : PlateStyle

    data object American : PlateStyle

    data object Universal : PlateStyle
}
