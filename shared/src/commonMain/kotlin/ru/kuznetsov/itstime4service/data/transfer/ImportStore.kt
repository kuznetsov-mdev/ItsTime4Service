package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.ensureActive
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

internal data class CapturedImportState(val state: ImportState, val digest: String)

internal class ImportStore(private val db: ItsTime4ServiceDatabase) {
    fun capture(now: Instant, zone: TimeZone, context: CoroutineContext): CapturedImportState {
        val cars = db.carQueries.selectAllWithSold().executeAsList()
        val tasks = db.maintenanceTaskQueries.selectAll().executeAsList()
        val history = db.serviceHistoryQueries.selectAll().executeAsList()
        val settings = db.settingsQueries.select().executeAsOneOrNull()
        val reminders = db.plannedReminderQueries.selectForTransfer().executeAsList()
        if (cars.size > TransferLimits.MAX_CARS || tasks.size > TransferLimits.MAX_TASKS || history.size > TransferLimits.MAX_HISTORY) {
            throw SnapshotRejected(TransferFailure.LimitExceeded)
        }
        val collector = TransferReminderCollector(tasks.map { it.id }, now, Instant.fromEpochMilliseconds(now.toEpochMilliseconds()), zone)
        reminders.forEach { row ->
            context.ensureActive()
            collector.add(row.task_id, row.fire_at, row.shown_at_utc)
        }
        val shown = collector.result()
        val provenance = db.transferQueries.selectProvenance().executeAsList().map {
            ImportProvenance(it.snapshot_id, it.source_digest, it.entity_kind, it.incoming_id, it.target_car_id, it.local_id, it.instance_id)
        }
        val snapshot = TransferSnapshotMapper(ExportSelection.FullBackup).map(
            "00000000-0000-4000-8000-000000000000", Instant.fromEpochMilliseconds(now.toEpochMilliseconds()),
            cars, tasks, history, tasks.associate { it.id to it.car_id }, settings, shown,
        )
        val rows = listOf(
            cars.map { listOf(it.id, it.brand, it.mileage_km, it.vin, it.is_sold, it.plate, it.plate_hidden, it.vin_hidden, it.maintenance_description) },
            tasks.map { listOf(it.id, it.car_id, it.name, it.interval_km, it.last_done_km, it.reminder_window_km, it.interval_months, it.last_done_date, it.icon) },
            history.map { listOf(it.id, it.car_id, it.task_name, it.date, it.mileage_km, it.cost_rub, it.service_name, it.task_id, it.comment, it.prev_last_done_km, it.prev_last_done_date, it.rollback_available) },
            listOfNotNull(settings?.let { listOf(it.id, it.theme_mode, it.distance_unit, it.push_enabled, it.default_reminder_window_km, it.reminder_time, it.last_opened_car_id, it.open_default_car, it.default_car_id, it.cars_view) }),
            reminders.map { listOf(it.task_id, it.fire_at, it.shown_at_utc) },
            db.transferQueries.selectSources().executeAsList().map { listOf(it.snapshot_id, it.source_digest) },
        )
        context.ensureActive()
        return CapturedImportState(ImportState(snapshot.cars, snapshot.settings.required(), shown, provenance),
            ImportFingerprint.database(rows, provenance, zone.id))
    }

    fun apply(plan: ImportPlan, before: ImportState, zone: TimeZone, context: CoroutineContext) {
        val changes = plan.validatedChanges
        val replace = plan.mode == ImportMode.REPLACE_BACKUP
        if (replace) {
            db.serviceHistoryQueries.deleteAll()
            db.maintenanceTaskQueries.deleteAll()
            db.carQueries.deleteAll()
        } else {
            val remainingTasks = changes.cars.flatMap { it.tasks }.map { it.id }.toSet()
            before.cars.forEach { car -> car.tasks.filter { it.id !in remainingTasks }.forEach { task ->
                context.ensureActive()
                db.maintenanceTaskQueries.deleteById(task.id, car.id)
            } }
        }
        val oldCars = before.cars.associateBy { it.id }
        changes.cars.forEach { car ->
            context.ensureActive()
            val original = if (replace) null else oldCars[car.id]
            if (original == null) db.carQueries.insert(car.id, car.brand, car.mileageKm.required().toLong(),
                car.vin.required(), car.plate.required(), car.maintenanceDescription)
            if (original != car) db.carQueries.updateImported(car.brand, car.mileageKm.required().toLong(), car.vin.required(),
                car.sold.required(), car.plate.required(), car.preferences.required().plateHidden, car.preferences.required().vinHidden,
                car.maintenanceDescription, car.id)
            val oldTasks = original?.tasks.orEmpty().associateBy { it.id }
            val oldHistory = original?.history?.required().orEmpty().associateBy { it.id }
            car.tasks.forEach { task ->
                context.ensureActive()
                val old = oldTasks[task.id]
                val service = task.lastService.required()
                if (old == null) {
                    db.maintenanceTaskQueries.insert(task.id, car.id, task.name, task.intervalKm.toLong(), service.mileageKm?.toLong(),
                        task.reminderWindowKm.required().toLong(), task.intervalMonths?.toLong(), service.date?.toString(), task.icon?.name)
                } else if (old != task) {
                    db.maintenanceTaskQueries.update(task.name, task.intervalKm.toLong(), service.mileageKm?.toLong(),
                        task.reminderWindowKm.required().toLong(), task.intervalMonths?.toLong(), service.date?.toString(), task.icon?.name, task.id, car.id)
                }
            }
            car.history.required().forEach { entry ->
                context.ensureActive()
                val old = oldHistory[entry.id]
                val details = entry.details.required()
                val rollback = entry.rollback.valueOrNull()
                if (old == null) {
                    db.serviceHistoryQueries.insert(entry.id, car.id, entry.taskName, entry.date.toString(), entry.mileageKm.toLong(),
                        details.costRub?.toLong(), details.serviceName, entry.taskId, details.comment,
                        rollback?.previous?.mileageKm?.toLong(), rollback?.previous?.date?.toString(), rollback != null)
                } else if (old != entry) {
                    db.serviceHistoryQueries.updateImported(entry.taskName, entry.date.toString(), entry.mileageKm.toLong(),
                        details.costRub?.toLong(), details.serviceName, entry.taskId, details.comment,
                        rollback?.previous?.mileageKm?.toLong(), rollback?.previous?.date?.toString(), rollback != null, entry.id, car.id)
                }
            }
        }
        val settings = changes.settings
        if (replace || settings != before.settings) db.settingsQueries.upsert(settings.themeMode.name, settings.distanceUnit.name,
            settings.pushEnabled, settings.defaultReminderWindowKm.toLong(), settings.reminderTime.toString(), settings.lastOpenedCarId,
            settings.openDefaultCar, settings.defaultCarId, settings.carsView.name)
        db.plannedReminderQueries.deleteAll()
        changes.reminders.forEach { db.plannedReminderQueries.insertShown(it.taskId, it.shownAt.toLocalDateTime(zone).toString(), it.shownAt.toString()) }
        db.transferQueries.insertSource(plan.snapshotId, plan.snapshotDigest)
        changes.provenance.forEach { row ->
            context.ensureActive()
            db.transferQueries.insertProvenance(row.snapshotId, row.sourceDigest, row.kind, row.incomingId, row.targetCarId, row.localId, row.instanceId)
        }
        db.transferQueries.insertOperation(plan.operationId, plan.snapshotId, plan.snapshotDigest, plan.databaseDigest,
            plan.decisionsDigest, plan.changesDigest, plan.mode.name, ImportJournalCodec.encode(plan.summary), NotificationState.PENDING.name)
        context.ensureActive()
    }

    fun operation(id: String, alreadyApplied: Boolean): ImportResult? = db.transferQueries.selectOperation(id).executeAsOneOrNull()?.let {
        ImportResult(it.operation_id, ImportJournalCodec.decode(it.summary), alreadyApplied, NotificationState.valueOf(it.notification_state))
    }
}
