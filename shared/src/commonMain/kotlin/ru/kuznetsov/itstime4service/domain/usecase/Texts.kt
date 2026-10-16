package ru.kuznetsov.itstime4service.domain.usecase

internal fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal const val MAX_PLATE_LENGTH = 12

internal fun normalizePlate(text: String?): String? =
    text?.trim()?.uppercase()?.replace(Regex("\\s+"), " ")?.take(MAX_PLATE_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
