package ru.kuznetsov.itstime4service.domain.transfer

object TransferLimits {
    const val FORMAT_VERSION = 1
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_METADATA_BYTES = 4 * 1024
    const val MAX_PLAINTEXT_BYTES = 10 * 1024 * 1024
    const val MAX_JSON_DEPTH = 24
    const val MAX_PASSWORD_BYTES = 1_024
    const val MAX_CARS = 1_000
    const val MAX_TASKS = 20_000
    const val MAX_HISTORY = 50_000
    const val MAX_REMINDERS = 20_000
    const val MAX_NAME_BYTES = 1_024
    const val MAX_IDENTIFIER_BYTES = 256
    const val MAX_DESCRIPTION_BYTES = 16_384
    const val MAX_INTERVAL_MONTHS = 120_000
}
