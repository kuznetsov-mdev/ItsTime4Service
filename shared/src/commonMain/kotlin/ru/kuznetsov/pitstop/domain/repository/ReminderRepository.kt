package ru.kuznetsov.pitstop.domain.repository

import ru.kuznetsov.pitstop.domain.model.ReminderRecord

/**
 * Storage of the notification plan, kept so that the next planning knows what has already been shown.
 * [replaceRecords] replaces the stored list as a whole.
 */
interface ReminderRepository {
    suspend fun getRecords(): List<ReminderRecord>

    suspend fun replaceRecords(records: List<ReminderRecord>)
}
