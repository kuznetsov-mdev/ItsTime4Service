package ru.kuznetsov.itstime4service.ui.format

/**
 * The country the device is set to, as an ISO 3166 code such as "RU", "US" or "ES", or `null` when it is unknown.
 * Picks the licence plate kind when the typed number has no recognisable format.
 */
expect fun deviceRegion(): String?
