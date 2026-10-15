package ru.kuznetsov.itstime4service.domain.usecase

internal fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
