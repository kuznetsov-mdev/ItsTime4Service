package ru.kuznetsov.pitstop.domain.usecase

internal fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
