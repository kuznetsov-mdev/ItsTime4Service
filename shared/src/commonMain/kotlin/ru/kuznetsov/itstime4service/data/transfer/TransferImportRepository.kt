package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase
import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class TransferImportRepository(
    private val db: ItsTime4ServiceDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZone: () -> TimeZone,
) : ImportRepository {
    private val store = ImportStore(db)

    override suspend fun fingerprint(snapshot: TransferSnapshot): TransferOutcome<String> = guarded(TransferFailure.InvalidSnapshot) {
        TransferValidator.validate(snapshot)
        ImportFingerprint.snapshot(snapshot)
    }

    override suspend fun readPreviousTaskMatches(snapshotId: String, snapshotDigest: String): TransferOutcome<Map<ImportTaskMatchKey, List<String>>> =
        guarded(TransferFailure.DatabaseReadFailed) {
            db.transferQueries.selectPreviousTaskMatches(snapshotId, snapshotDigest).executeAsList()
                .groupBy({ ImportTaskMatchKey(it.target_car_id, it.incoming_id) }, { it.local_id })
        }

    override suspend fun prepareImport(request: ImportRequest): TransferOutcome<PreparedImport> = guarded(TransferFailure.DatabaseReadFailed) {
        TransferValidator.validate(request.snapshot)
        val snapshot = TransferDocument.decode(TransferDocument.encode(request.snapshot))
        val digest = ImportFingerprint.snapshot(snapshot)
        if (request.snapshotDigest != digest) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
        if ((snapshot.kind == TransferKind.CAR) != (request.mode == ImportMode.INTO_CAR)) {
            throw SnapshotRejected(TransferFailure.InvalidSnapshot)
        }
        val decisions = request.decisions.captured()
        validateDecisions(snapshot, request.mode, decisions)
        val context = currentCoroutineContext()
        db.transactionWithResult {
            val prior = db.transferQueries.selectSources().executeAsList().find { it.snapshot_id == snapshot.snapshotId }
            if (prior != null && prior.source_digest != digest) throw SnapshotRejected(TransferFailure.SourceIdentityConflict)
            val now = clock.now()
            val current = store.capture(now, timeZone(), context)
            val decisionsDigest = ImportFingerprint.decisions(request.mode, decisions)
            when (val prepared = ImportPlanner({ ImportFingerprint.localId(digest, current.digest, it) }, {
                ImportFingerprint.key(digest, current.digest, it)
            }).prepare(snapshot, digest, request.mode, decisions, current.state)) {
                is ImportPreparation.NeedsDecisions -> PreparedImport.NeedsDecisions(prepared.decisions)
                is ImportPreparation.Ready -> {
                    val checked = TransferSnapshot(1, snapshot.snapshotId,
                        maxOf(snapshot.createdAt, kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds())), TransferKind.BACKUP,
                        TransferGroup.entries.toSet(), prepared.changes.cars, Presence.Present(prepared.changes.settings), Presence.Present(prepared.changes.reminders))
                    TransferValidator.validate(checked)
                    PreparedImport.Ready(ImportPlan(Uuid.random().toString(), snapshot.snapshotId, digest, current.digest, decisionsDigest,
                        request.mode, prepared.summary, prepared.changes, ImportFingerprint.changes(prepared.changes)))
                }
            }
        }
    }

    override suspend fun applyImport(plan: ImportPlan): TransferOutcome<ImportResult> = guarded(TransferFailure.DatabaseWriteFailed) {
        val context = currentCoroutineContext()
        db.transactionWithResult {
            context.ensureActive()
            val prior = db.transferQueries.selectOperation(plan.operationId).executeAsOneOrNull()
            if (prior != null) {
                if (prior.snapshot_id != plan.snapshotId || prior.source_digest != plan.snapshotDigest ||
                    prior.decisions_digest != plan.decisionsDigest || prior.changes_digest != plan.changesDigest ||
                    prior.database_digest != plan.databaseDigest || prior.mode != plan.mode.name ||
                    prior.summary != ImportJournalCodec.encode(plan.summary)) {
                    throw SnapshotRejected(TransferFailure.InvalidOperationReuse)
                }
                return@transactionWithResult requireNotNull(store.operation(plan.operationId, true))
            }
            val zone = timeZone()
            val before = store.capture(clock.now(), zone, context)
            if (before.digest != plan.databaseDigest) throw SnapshotRejected(TransferFailure.StalePlan)
            if (ImportFingerprint.changes(plan.validatedChanges) != plan.changesDigest) {
                throw SnapshotRejected(TransferFailure.InvalidOperationReuse)
            }
            val priorSource = db.transferQueries.selectSources().executeAsList().find { it.snapshot_id == plan.snapshotId }
            if (priorSource != null && priorSource.source_digest != plan.snapshotDigest) {
                throw SnapshotRejected(TransferFailure.SourceIdentityConflict)
            }
            store.apply(plan, before.state, zone, context)
            ImportResult(plan.operationId, plan.summary, false, NotificationState.PENDING)
        }
    }

    override suspend fun findOperation(operationId: String): TransferOutcome<ImportResult?> = guarded(TransferFailure.DatabaseReadFailed) {
        store.operation(operationId, true)
    }

    override suspend fun pendingNotificationOperations(): TransferOutcome<List<String>> = guarded(TransferFailure.DatabaseReadFailed) {
        db.transferQueries.selectPendingOperations().executeAsList()
    }

    override suspend fun recordNotificationState(operationId: String, state: NotificationState): TransferOutcome<ImportResult> = guarded(TransferFailure.DatabaseWriteFailed) {
        db.transactionWithResult {
            if (store.operation(operationId, true) == null) throw SnapshotRejected(TransferFailure.InvalidOperationReuse)
            db.transferQueries.updateNotificationState(state.name, operationId)
            requireNotNull(store.operation(operationId, true))
        }
    }

    private fun validateDecisions(snapshot: TransferSnapshot, mode: ImportMode, decisions: ImportDecisions) {
        if (mode == ImportMode.REPLACE_BACKUP) return
        val refs = snapshot.cars.flatMap { car -> car.tasks.map { EntityRef(car.id, it.id) } }.toSet()
        if (!refs.containsAll(decisions.selectedTasks) || !decisions.selectedTasks.containsAll(decisions.taskChoices.keys) ||
            !decisions.selectedTasks.containsAll(decisions.taskTargets.keys) ||
            decisions.carTargets.values.filterIsInstance<TargetCar.New>().any {
                it.confirmedMileageKm < 0 || it.brand.encodeToByteArray(throwOnInvalidSequence = true).size > TransferLimits.MAX_NAME_BYTES
            }) throw SnapshotRejected(TransferFailure.InvalidSnapshot)
    }

    private fun ImportDecisions.captured() = copy(carTargets = carTargets.toMap(), selectedTasks = selectedTasks.toSet(),
        taskChoices = taskChoices.toMap(), taskTargets = taskTargets.toMap(), personalChoices = personalChoices.toMap(),
        relatedChoices = relatedChoices.toMap(), soldChoices = soldChoices.toMap())

    private suspend fun <T> guarded(failure: TransferFailure, block: suspend () -> T): TransferOutcome<T> = withContext(dispatcher) {
        try {
            TransferOutcome.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rejected: SnapshotRejected) {
            TransferOutcome.Failure(rejected.reason)
        } catch (_: Exception) {
            TransferOutcome.Failure(failure)
        }
    }
}
