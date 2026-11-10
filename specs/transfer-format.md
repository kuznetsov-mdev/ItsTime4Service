# Transfer format v1 — wire and operation contract

Frozen 2026-11-10 from the coordinator-corrected contract. This document defines v1 bytes, validation and operation semantics for implementation; it does not claim that the codec, import planner or native file ports already exist. Product authority: [export/import requirements](export-import.md). Executed crypto evidence and remaining release gates: [compatibility report](../docs/research/transfer-format-compatibility.md).

All requirements below are normative for v1. Domain shapes describe validated values; wire fields are the explicit JSON mapping. Stage 1B introduced isolated proof dependencies; stage 4 now connects the pinned providers/JSON implementation to production source sets. This does not complete the feature or its release validation.

## 1. Envelope

All integer prefix fields are unsigned, big-endian. Fixed prefix is exactly 14 bytes:

| Offset | Length | Value |
|---|---:|---|
| 0 | 4 | ASCII `IT4S` (`49 54 34 53`) |
| 4 | 1 | envelope version `1` |
| 5 | 1 | protection: `0` plain, `1` encrypted |
| 6 | 4 | metadata byte length |
| 10 | 4 | body byte length |

Exactly prefix + metadata + body; truncation and trailing bytes are errors. Validate lengths with checked 64-bit arithmetic before allocation. Read a bounded stream even if the picker reports a smaller size. Total ≤ 16 MiB; metadata ≤ 4 KiB; plaintext body ≤ 10 MiB; encrypted body ≤ 10 MiB + 16 bytes. No compression, base64 wrapping of the whole body, SQLite file or external attachment.

Metadata is strict UTF-8 JSON without BOM. Plain metadata is `{}`. Encrypted metadata has exactly `cipher:"AES-256-GCM"`, `kdf:"PBKDF2-HMAC-SHA256"`, `iterations:600000`, `salt:<base64 16 bytes>`, `nonce:<base64 12 bytes>`, `tagBytes:16`. Base64 is canonical RFC 4648 standard alphabet, padded. Reject unknown algorithm, parameter, field, duplicate key or wrong length before KDF work. Kind, IDs, names, dates, counts and descriptions appear only in payload. AAD is the original 14 prefix bytes followed by the original metadata bytes, never reserialized JSON. Body is ciphertext followed by 16-byte tag; nonce is not duplicated in body. AES key is 32 bytes. New salt and nonce from a cryptographic random source on every encryption.

Password bytes: exact valid Unicode input encoded as UTF-8; no trimming, case conversion or normalization. Reject unpaired UTF-16 surrogates before encoding, empty password and > 1,024 UTF-8 bytes. Visually equal composed/decomposed passwords are different; UI must not silently modify them. This byte contract must be verified against both providers before adoption. Secret buffers are scoped to one operation and cleared in `finally` where mutable; immutable UI strings/managed runtime copies cannot be promised securely erasable. No secret is serializable or logged.

Provider pin for the proof and stage 4: cryptography-kotlin 0.6.0; Android explicit `CryptographyProvider.JDK(BouncyCastleProvider())` with `bcprov-jdk18on:1.83`; iOS `CryptographyProvider.Openssl3` with API + prebuilt-nativebuilds modules and a strict `com.ensody.nativebuilds:openssl-libcrypto:3.6.5` dependency. The earlier prebuilt module with embedded OpenSSL 3.6.0 has been replaced. Avoid system-provider PBKDF2 below API26. Stage 4 binds these providers at platform leaves and connects their runtime dependencies. Actual primitive evidence and unresolved release checks belong to the [compatibility report](../docs/research/transfer-format-compatibility.md); passing a primitive proof does not complete saved-file compatibility or minimum-OS performance validation. JSON uses kotlinx-serialization-json 1.12.0 behind a bounded structural reader and explicit field/token mappers, with duplicate decoded-key rejection before insertion, no input in parser diagnostics and no domain serialization annotations; rationale in the [ADR](../docs/adr/0001-transfer-json-codec.md).

Protection policy: plain payload is permitted for every valid snapshot kind and group composition. Backup and personal selective exports default to protection 1, but the user may explicitly disable it after reviewing the disclosure; pure templates default to protection 0. An importer cannot infer export consent from group composition and must not reject a valid plaintext personal snapshot solely because it is plaintext. An open file has no claim of authorship/authenticity; modification is detected only when it violates syntax/validation. For encrypted input, decode no payload and disclose no preview before authenticating all bytes. Wrong password and altered ciphertext/tag/AAD yield the same `AuthenticationFailed` result. Structurally unsupported headers can be rejected before authentication without revealing personal content.

## 2. Payload, presence and bounds

Root fields: `formatVersion:1`, `snapshotId:<UUID>`, `createdAt:<UTC instant>`, `kind:"backup"|"car"`, `includedGroups:[...]`, `cars:[...]`; optional `settings` and `reminderState` exactly as allowed by groups. `snapshotId` is newly generated once per export artifact and retained when that artifact is copied. No installation/device/account identifier. Envelope and payload versions both must be supported; reject newer versions with an update suggestion.

Group tokens, in this fixed emission order: `mileage`, `vin`, `plate`, `lastService`, `history`, `historyDetails`, `preferences`, `rollback`, `reminderState`. Any nonempty set of these groups is a personal composition; an empty set is a pure template. Backup requires the entire set. Car allows only the first six. `historyDetails` requires `history`. `rollback` requires history and lastService and is backup-only. Arrays have no duplicate group tokens; unknown token/enum/field is rejected in v1, rather than silently granting a future group permission.

Presence is not encoded by null. An excluded group means its fields are absent; any field forbidden by the declared groups makes the file invalid. Included fields must exist, even if nullable. Null is allowed only for a declared nullable fact; it never means permission is absent. A selective receiver still needs an explicit decision to apply an included personal field. `TakeIncoming(null)` clears an existing nullable value only after the preview explicitly shows that consequence. Default is `KeepLocal`.

Strict parser: reject duplicate object keys, invalid UTF-8, comments, trailing commas, NaN/Infinity, fractional/exponent notation for integer fields and depth > 24. Do not first materialize unbounded JSON and then count it. Limits apply during parsing and before constructing the domain snapshot: ≤ 1,000 cars, ≤ 20,000 tasks total, ≤ 50,000 history rows total, ≤ 20,000 reminder rows total; UUID/IDs ≤ 64 UTF-8 bytes; brand/task/service names ≤ 1,024 bytes; VIN/plate ≤ 256 bytes; description/comment ≤ 16,384 bytes each. File and plaintext limits still dominate all per-field limits. Oversized local exports fail explicitly, never truncate.

Distances/costs/windows: integers 0..2,147,483,647; intervalKm 1..2,147,483,647; intervalMonths null or 1..120,000. Derived arithmetic uses checked Long before mapping to Int. Dates: real ISO `YYYY-MM-DD`, years 0001..9999. Times: `HH:mm:ss`; creation and shown-reminder instants: UTC `YYYY-MM-DDTHH:mm:ss.SSSZ`. Kilometres and whole rubles are independent of UI locale. Text is preserved, without translating names or Unicode normalization. Scalar and aggregate limits require fixture tests at boundary and boundary+1 before finalizing v1.

## 3. Domain data shapes

The following pseudo-Kotlin defines names, ownership and semantics; omitted wire fields map to `Absent`, not defaults. `TransferSnapshot` is validated; decoding raw DTOs cannot produce it without all invariants passing. No Compose/platform/JSON/crypto type is exposed in domain.

```kotlin
sealed interface Presence<out T> {
    data object Absent : Presence<Nothing>
    data class Present<T>(val value: T) : Presence<T>
}
enum class TransferKind { BACKUP, CAR }
enum class TransferGroup {
    MILEAGE, VIN, PLATE, LAST_SERVICE, HISTORY, HISTORY_DETAILS,
    PREFERENCES, ROLLBACK, REMINDER_STATE
}
data class LastService(val mileageKm: Int?, val date: LocalDate?)
data class HistoryDetails(val costRub: Int?, val serviceName: String?, val comment: String?)
data class RollbackData(val previous: LastService)
data class TransferTask(
    val id: String, val name: String, val intervalKm: Int,
    val intervalMonths: Int?, val icon: TaskIcon?,
    val lastService: Presence<LastService>,
    val reminderWindowKm: Presence<Int>
)
data class TransferHistory(
    val id: String, val taskId: String?, val taskName: String,
    val date: LocalDate, val mileageKm: Int,
    val details: Presence<HistoryDetails>,
    val rollback: Presence<RollbackData?>
)
data class CarPreferences(val plateHidden: Boolean, val vinHidden: Boolean)
data class TransferCar(
    val id: String, val brand: String, val maintenanceDescription: String?,
    val sold: Presence<Boolean>,
    val mileageKm: Presence<Int>, val vin: Presence<String?>,
    val plate: Presence<String?>, val preferences: Presence<CarPreferences>,
    val tasks: List<TransferTask>, val history: Presence<List<TransferHistory>>
)
data class TransferReminder(val taskId: String, val shownAt: Instant)
data class TransferSnapshot(
    val formatVersion: Int, val snapshotId: String, val createdAt: Instant,
    val kind: TransferKind, val includedGroups: Set<TransferGroup>,
    val cars: List<TransferCar>, val settings: Presence<Settings>,
    val reminderState: Presence<List<TransferReminder>>
)
sealed interface ExportSelection {
    data object FullBackup : ExportSelection
    data class Car(
        val carId: String, val taskIds: Set<String>,
        val personalGroups: Set<TransferGroup>, val maintenanceDescription: String?
    ) : ExportSelection
}
```

`LocalDate`, `Instant`, `Settings` and enums above are project/domain types, with explicit wire DTO mappers. Format enums use frozen string tokens, not enum ordinal; new enum values require explicit compatibility policy. `maintenanceDescription` maps to `Car/NewCar.maintenanceDescription`, optional authored applicability/source text, never automatically labelled verified. Backup exports the stored description; car exports the author's confirmed text. It must not auto-copy VIN, plate or hidden values into description. Step 2's `ServiceHistoryEntry.rollbackAvailable` is independent of nullable `prevLastDoneKm`: false maps to backup `rollback:null`, true maps to a `RollbackData` object whose previous mileage may be null. Import writes both the explicit eligibility flag and nullable predecessor fields. Existing explicit zero remains zero.

Wire objects use the property names above, except `Presence` wrappers are not serialized. `LastService` is emitted as `lastService:{mileageKm,date}` when included; details as `details:{costRub,serviceName,comment}`; rollback as `rollback:null|{previous:{mileageKm,date}}`. Task windows and car hidden/sold flags exist only in backup. Settings emits every current Settings field, including explicit nullable car references; an empty settings table is exported as its effective defaults. `settings` and car/task preferences all belong to `preferences` group.

`car` contains exactly one car, no sold flag, no preferences, no task windows, no reminders, no rollback. `backup` permits zero cars and always includes settings and reminder arrays. A sold backup car has zero active tasks. Car/task/history IDs are unique in their own global entity namespace in a snapshot. Backup IDs are canonical lowercase UUID strings, preserved exactly. Car export assigns sequential file-local `c1`, `t1`, `h1` IDs after filtering, never includes source UUIDs. Deleted/unselected task references are either consistently remapped to opaque historical `d1`, `d2` tokens or null for originally unlinked manual events; `d` tokens have no task objects and must never create tasks. Do not use deterministic hashes of source UUIDs as selective IDs.

Export group permissions are applied before encoding or fingerprinting a selective payload. Tests must search both the payload and all metadata/reference/rollback/description fields for excluded facts. History is the whole selected car history independently of selected works; no inclusion of history can add task objects or last-service facts. Historical task name/date/mileage are part of explicitly selected history. Cost/service/comment require historyDetails. Selective history carries no previous-service rollback facts.

## 4. Identity, order, links and rollback

- Arrays preserve meaningful order. No independent `sortOrder` field: car array is active cars in local order then sold cars in local order; tasks/history use persisted insertion order within each car. For equal history dates array order breaks the last-added tie. Restore inserts accordingly. Merge preserves positions of local entities; new entities append in incoming order. Update does not reorder existing rows.
- Full-backup task/history links can reference absent deleted tasks with valid UUIDs; a link to an existing task belonging to another car is invalid. Structural car ownership references for tasks/history must resolve within the snapshot. Settings lastOpenedCarId/defaultCarId may be stale (missing or sold); accept them as nullable identity strings and apply the remap/null/openDefaultCar=false policy from section 6 rather than rejecting the snapshot. The import plan maps every selected incoming ID to a local ID or an explicit historical-only identity. It never guesses by brand/VIN. For selective historical-only task IDs, allocate stable local tombstone UUID mappings without a maintenance row.
- Stable UUID or previous import provenance identifies a matching candidate, never permission to overwrite conflicting facts. Names can suggest task matches, but cannot silently merge. When identity collides across different car ownership, require explicit remapping/add-separate or fail validation; never repoint existing rows.
- `lastService.mileageKm=null` means unknown; `date=null` means unknown date when intervalMonths exists, and no stored date when there is no temporal criterion. Explicit zero stays zero. History never fills absent lastService automatically.
- `rollback=Absent` means no permission to import rollback. In backup, `Present(null)` means the record is not an eligible rollback candidate; `Present(RollbackData(previous))` can contain previous.mileageKm=null legitimately. Eligibility is NOT inferred from that mileage. A candidate requires a matching active same-car task, latest eligible linked history fact and current lastService equal to that record's date/mileage. Invalid rollback input is rejected or explicitly disabled by the plan with a reason, never silently enabled.
- Conflicts form connected components over a task's current lastService, linked imported/local latest history fact and rollback predecessor. Group only components whose chosen combinations would break a relation. Independent manual history and unrelated lastService decisions remain separate. `KeepLocal` / `TakeIncoming` acts on the entire conflicting component; the preview lists all affected facts. Parameter-only task update preserves local facts. Conflict disables affected old rollback candidates while retaining history; new future marks still get normal rollback.
- Sold/active conflict is explicit per car, with counts and consequences. Choosing sold removes active works only when that destructive consequence is in the confirmed plan; choosing active does not resurrect unspecified works. Replacement follows the complete snapshot. No invisible active works remain under sold cars.

## 5. Requests, plans, results and repeated delivery

```kotlin
enum class ImportMode { REPLACE_BACKUP, MERGE_BACKUP, INTO_CAR }
sealed interface TargetCar {
    data class Existing(val localCarId: String) : TargetCar
    data class New(val brand: String, val confirmedMileageKm: Int) : TargetCar
}
enum class TaskChoice { SKIP, UPDATE_PARAMETERS, ADD_SEPARATE }
enum class PersonalChoice { KEEP_LOCAL, TAKE_INCOMING }
enum class BackupSafety { NOT_REQUIRED, SAVED, EXPLICITLY_DECLINED }
data class EntityRef(val carId: String, val entityId: String)
data class ImportDecisions(
    val carTargets: Map<String, TargetCar>,
    val selectedTasks: Set<EntityRef>,
    val taskChoices: Map<EntityRef, TaskChoice>,
    val taskTargets: Map<EntityRef, String>,
    val personalChoices: Map<String, PersonalChoice>,
    val relatedChoices: Map<String, PersonalChoice>,
    val soldChoices: Map<String, PersonalChoice>,
    val restoreSettings: Boolean, val backupSafety: BackupSafety
)
data class ImportRequest(
    val snapshot: TransferSnapshot, val snapshotDigest: String,
    val mode: ImportMode, val decisions: ImportDecisions
)
data class PendingDecision(val id: String, val affected: Set<EntityRef>, val reason: String)
data class ImportSummary(
    val carsAdded: Int, val carsUpdated: Int, val carsRemoved: Int,
    val tasksAdded: Int, val tasksUpdated: Int, val tasksRemoved: Int,
    val historyAdded: Int, val historyUpdated: Int, val historyRemoved: Int,
    val skipped: Int, val settingsRestored: Boolean,
    val rollbackDisabled: Set<EntityRef>
)
sealed interface PreparedImport {
    data class NeedsDecisions(val decisions: List<PendingDecision>) : PreparedImport
    data class Ready(val plan: ImportPlan) : PreparedImport
}
data class ImportPlan internal constructor(
    val operationId: String, val snapshotId: String, val snapshotDigest: String,
    val databaseDigest: String, val decisionsDigest: String,
    val mode: ImportMode, val summary: ImportSummary,
    internal val validatedChanges: ImportChangeSet
)
enum class NotificationState { PENDING, APPLIED, RETRY_REQUIRED }
data class ImportResult(
    val operationId: String, val summary: ImportSummary,
    val alreadyApplied: Boolean, val notifications: NotificationState
)
```

`ImportChangeSet` is an opaque immutable validated snapshot of exact inserts, updates, removals, settings changes and ID/provenance mappings, owned by the domain import planner. It contains no arbitrary SQL/callbacks. `personalChoices` keys and related/sold decision IDs are planner-issued stable opaque keys bound to this source/target/database state, not UI-synthesized field paths. Missing required decisions produce NeedsDecisions, never implicit destructive defaults. ImportPlan constructor is not exposed to UI. Summary and mutations come from the same change set.

For backup merge, carTargets covers EVERY incoming car including sold cars before Ready. For INTO_CAR it contains exactly one target. A new target always has confirmed actual mileage; refusal of incoming mileage requires entering another value. Existing target preserves absent personal fields. All incoming tasks start selected; coinciding task choice is SKIP by default, while an unmatched selected task is a planned add. ADD_SEPARATE is always explicit for a match. New task reminder window uses recipient's effective setting; existing task parameter update preserves its window. New task lastService is unknown unless incoming facts are permitted and explicitly accepted.

User clarification 2026-11-10: UPDATE_PARAMETERS identifies one concrete local task. `taskTargets` maps the selected incoming EntityRef to that local task ID. If multiple same-name candidates exist, the UI presents each with interval and last-service details; none is preselected. A unique candidate may be shown as the target of the explicit update command. The planner verifies target ownership/existence and a one-to-one mapping for updated tasks; conflicting choices produce NeedsDecisions, never first-match/last-write-wins behaviour. The final preview repeats the concrete target details. Changing the source/target car clears these mappings; SKIP/ADD_SEPARATE do not carry a prior update target. The on-disk v1 format is unchanged: this is an import-operation decision, not a new wire field.

REPLACE_BACKUP ignores merge-only choices and restores the complete snapshot, with only restoreSettings and final safety gate affecting behaviour. No skip/add-separate choices are rendered or accepted. Saved safety means a positively acknowledged file-save result, not merely opening a share sheet. Prepare can preview before safety is resolved, but Ready for final apply requires SAVED or EXPLICITLY_DECLINED. A stale database requires rebuilding preview and final confirmation including the safety consequences.

Hashes are SHA-256 of a versioned deterministic encoding of validated values: fixed field order and type tags; length-prefixed UTF-8 strings; explicit absent/null tags; fixed-width integers; ordered lists preserved; map/set keys sorted. No hashing `toString()`, locale text or ordinary unordered JSON output. Snapshot digest covers all logical payload values and includedGroups, not randomized envelope bytes; database digest covers all affected current data, order, settings, reminder state and import provenance (excluding the operation journal entry being queried). Different raw JSON with identical validated values has the same digest. Also retain raw-file digest within session for detecting a changed source during reading, not as an entity identity.

Idempotence has two independent mechanisms:

1. A random operationId is minted once for a confirmed Ready plan. In the same transaction as all mutations, persist operationId + source/decision digests + result. Repeated apply first looks up this entry; identical identity returns the saved result without writes, even if current DB digest is now different. Same operationId with different digests fails. The ledger survives replacements and is not exported/imported as personal user data.
2. Persist provenance `(snapshotId, incomingKind, incomingEntityId, localTargetCarId) -> localEntityId` with source digest for every imported entity, including tombstone references. Reopening a file builds matches from provenance and current DB; it must not mint new automatic copies. Same snapshotId with different source digest is an identity conflict, never silently trusted. If a prior mapped entity was deleted, show that as a decision; do not automatically recreate it. A different target car or explicit ADD_SEPARATE can intentionally create new mappings, but only under a newly confirmed operation. The ledger keeps distinct duplicate instances instead of overwriting the original provenance.

Fresh export has a new snapshotId; backup stable entity UUIDs still identify candidate matches. Selective fresh exports have no cross-file stable identity by design: name-based matches remain explicit. Reopening must not automatically reapply an earlier decision or resurrect its success screen; it rebuilds current preview. After process death the user reselects the file; provenance and operation results prevent uncontrolled copies. Finishing/cancelling drops the session; changing source/target discards all decisions and plan.

## 6. Settings, notifications, atomicity and ports

restoreSettings=true uses incoming backup settings in both replacement and merge; false retains the recipient settings. Resolve default/last-opened car references through the confirmed map; missing or sold targets become null, and openDefaultCar becomes false if no valid default remains. Preserve theme, units, app push preference, window, time and list layout exactly. OS permission is never imported. Retained recipient settings are also checked after replacement against surviving IDs. Selective import never restores settings or hidden flags.

ReminderState stores at most one last-shown semantic trace `(taskId, shownAt)` per extant active task, never future planned reminders, OS task IDs, paths, translated notification text or permission. At one captured snapshot boundary, retain existing naive-local ReminderRecord.fireAt values <= captureNowLocal, drop stale/dangling rows and choose the maximum per task. Convert that local timestamp using kotlinx.datetime `LocalDateTime.toInstant(capturedTimeZone)` (the project-pinned conversion policy for overlap/gap local times); encode shownAt in UTC `YYYY-MM-DDTHH:mm:ss.SSSZ`, like createdAt. Do not export the timezone or future planned rows. The legacy local timestamp is an approximation of the application's shown/pending distinction and is not proof that the OS physically delivered a notification.

On import, map task IDs and convert shownAt Instant to the receiver's current LocalDateTime only when adapting to the existing ReminderRecord model. Preserve the represented instant across zones; never reinterpret a source local clock value directly in the receiver's zone. A shownAt later than the captured export createdAt is invalid. On snapshot creation, timestamps with sub-millisecond precision are floored to milliseconds before the createdAt comparison. Replace restores mapped last-shown traces; merge retains the latest valid shownAt per relevant task after mapping, unioning recipient traces. Conflicting task facts invalidate their imported shown traces and schedule from final data. OS future planning always rebuilds from final task facts, current time and receiver permissions. Store a notification-reconciliation pending marker in the transaction. A post-commit failure returns committed ImportResult(RETRY_REQUIRED); retry scheduler only, never applyImport again. Snapshot/apply tests must cover source/receiver zones in both directions, DST ambiguity/nonexistent local times under the platform conversion policy, stale/future rows and already-shown versus upcoming notifications; existing legacy approximation must be documented.

All repository/file/codec/cipher operations are `suspend`; expensive parse/KDF/IO is off the UI thread. Required semantic ports:

```kotlin
interface TransferRepository {
    suspend fun readSnapshot(selection: ExportSelection): TransferOutcome<TransferSnapshot>
    suspend fun prepareImport(request: ImportRequest): TransferOutcome<PreparedImport>
    suspend fun applyImport(plan: ImportPlan): TransferOutcome<ImportResult>
    suspend fun findOperation(operationId: String): TransferOutcome<ImportResult?>
}
interface TransferCodec {
    suspend fun encode(snapshot: TransferSnapshot): TransferOutcome<ByteArray>
    suspend fun decode(plaintext: ByteArray): TransferOutcome<TransferSnapshot>
}
interface TransferCipher {
    suspend fun seal(plaintext: ByteArray, password: SecretBytes): TransferOutcome<ByteArray>
    suspend fun open(envelope: ByteArray, password: SecretBytes): TransferOutcome<ByteArray>
}
```

`SecretBytes` is a non-serializable closeable mutable UTF-8 byte holder scoped by the caller; cipher borrows it only during the suspend call, zeroes its derived key/internal copies in finally and never retains it. EnvelopeReader/Writer handles bounded framing and dispatch for plain/encrypted files in data; the cipher's seal/open names denote the complete encrypted envelope so AAD cannot be omitted by a caller. ExportSelection filters the snapshot before codec; codec validates again. No password in ImportRequest/Plan/Result.

Platform file port returns session-only `PickedFileHandle` tokens and distinct `Cancelled`, `Saved`, `Shared` results. It owns native descriptors/security-scoped access and always closes them, including cancellation. Read yields at most the validated bound; save success requires fully written/flushed/closed output and acknowledged platform result. Share success means the OS accepted a handoff, not proof another app received/persisted the file. Temporary files remain private, live only for the transfer/share lifecycle and follow a separately implemented cleanup policy; no deletion of user-picked files. Tokens/URIs/URLs never enter serialized routes.

Apply uses the existing serial database dispatcher and one transaction; recheck database digest inside it. Mutations, mappings, operation result and reconciliation marker commit together. Cancellation before commit rolls back. Cancellation after commit may prevent delivery to UI but must not be translated into rolled-back data; query the ledger on recovery. CancellationException propagates rather than being caught as an ordinary IO/DB error. No system file/notification operation runs inside the database transaction.

```kotlin
sealed interface TransferOutcome<out T> {
    data class Success<T>(val value: T) : TransferOutcome<T>
    data class Failure(val reason: TransferFailure) : TransferOutcome<Nothing>
}
sealed interface TransferFailure {
    data object PermissionDenied : TransferFailure
    data object FileUnavailable : TransferFailure
    data object InsufficientSpace : TransferFailure
    data object ReadFailed : TransferFailure
    data object WriteFailed : TransferFailure
    data object InvalidEnvelope : TransferFailure
    data object UnsupportedVersion : TransferFailure
    data object UnsupportedProtection : TransferFailure
    data object LimitExceeded : TransferFailure
    data object AuthenticationFailed : TransferFailure
    data object InvalidSnapshot : TransferFailure
    data object SourceIdentityConflict : TransferFailure
    data object StalePlan : TransferFailure
    data object InvalidOperationReuse : TransferFailure
    data object DatabaseReadFailed : TransferFailure
    data object DatabaseWriteFailed : TransferFailure
}
```

Picker/user cancellation is a normal separate UI result, not TransferFailure. Preview NeedsDecisions is not an error. Notification retry is an ImportResult state, not a failed import. Failure payloads do not carry source text, filenames, secrets or native exception messages; redacted internal diagnostics use fixed reason codes only. Failure before database commit leaves data unchanged.

## 7. Requirement trace and implementation checks

| Approved criterion | Contract responsibility / required evidence |
|---|---|
| 1 | Snapshot completeness, identity/order/link/rollback; both native directions still pending |
| 2 | Replace safety + stale-plan digest + one transaction; failure-injection test |
| 3 | Every-car map + explicit match decisions; absent preserves; multi-car fixture |
| 4 | restoreSettings both modes + remapped startup refs; settings matrix |
| 5 | Groups constrain every field before serialization; privacy fixtures |
| 6 | All tasks initially selected; historical-only IDs never create work |
| 7 | New mileage confirmation; included nullable versus absent personal fields |
| 8 | Explicit task choices; parameter-only mutation excludes factual servicing |
| 9 | Independent lastService/history; nullable mileage and no inferred completion |
| 10 | Authenticated envelope/password lifecycle; see compatibility report for executed proof |
| 11 | Explicit sold conflict and consequences; zero active tasks under sold cars |
| 12 | Explicit rollback eligibility and connected conflict components |
| 13 | Operation ledger + entity provenance + explicit separate-copy decision |
| 14 | Bounded native file port and distinct cancellation/save/share results |
| 15 | Commit then reconcile; persisted marker; scheduler-only retry |
| 16 | Fixed units/dates and untranslated text; nullable migration + locale tests |
| 17 | No scheduling of backups, folder persistence or stored keys |
| U1 | Kind and group composition inside validated payload; plaintext remains permitted without authenticity claim |
| U2 | Source/target changes invalidate complete decisions/plan |
| U3 | Replace has no merge decisions; positive backup-save acknowledgement |
| U4 | Finish destroys session; NeedsDecisions includes any unresolved personal/link choice |
| U5 | Normal picker cancellation; Android SAF without broad storage permission |

The compatibility report records only executed crypto checks. Codec boundary/privacy tests, real file exchange, maximum-size memory, release R8/license/size review and API24 physical-device timing remain implementation or pre-release gates; this contract does not mark them passed.

## 8. Exact JSON tokens and field presence

All listed object properties are required unless their group excludes them. Excluded means the property must be absent, never JSON null. No other properties are valid. Object member order is insignificant on decoding; emit the table order. Do not reserialize metadata when constructing AAD. Arrays preserve the order defined in section 4. JSON text has no BOM, whitespace is allowed only where JSON permits it, strings are Unicode scalar sequences, and UTF-8 must decode strictly. Text limits count decoded UTF-8 bytes, not the length of escape sequences. JSON null is accepted only at the nullable positions listed here. Export emits exact integers in decimal without exponent/fraction notation.

| Object | Properties in emission order | Group/presence rule |
|---|---|---|
| Root | `formatVersion`, `snapshotId`, `createdAt`, `kind`, `includedGroups`, `cars`, `settings`, `reminderState` | Last two properties present only for backup preferences/reminderState groups |
| Car | `id`, `brand`, `maintenanceDescription`, `sold`, `mileageKm`, `vin`, `plate`, `preferences`, `tasks`, `history` | Description nullable and always present. sold/preferences backup-only with preferences; mileageKm/vin/plate/history follow respective groups. mileageKm is non-null when included; vin/plate nullable. tasks always present |
| Task | `id`, `name`, `intervalKm`, `intervalMonths`, `icon`, `lastService`, `reminderWindowKm` | intervalMonths/icon nullable and always present; lastService follows lastService group; reminderWindowKm backup preferences only |
| LastService | `mileageKm`, `date` | Both required, each nullable; object itself non-null |
| History | `id`, `taskId`, `taskName`, `date`, `mileageKm`, `details`, `rollback` | taskId nullable; details follows historyDetails; rollback backup rollback group only, nullable |
| HistoryDetails | `costRub`, `serviceName`, `comment` | All three required, each nullable; object itself non-null |
| RollbackData | `previous` | LastService object; not nullable |
| CarPreferences | `plateHidden`, `vinHidden` | Both boolean |
| Reminder | `taskId`, `shownAt` | Non-null task ID and UTC instant, same format as createdAt; last-shown only |
| Settings | `themeMode`, `distanceUnit`, `pushEnabled`, `defaultReminderWindowKm`, `reminderTime`, `lastOpenedCarId`, `openDefaultCar`, `defaultCarId`, `carsView` | All required; only lastOpenedCarId/defaultCarId nullable. Boolean fields never numeric |

`Settings.reminderTime` uses `HH:mm:ss`; task `icon` is null or one token from `wrench`, `droplet`, `disc`, `filter`, `zap`, `battery`, `thermometer`, `tire`, `wind`, `gear`, `gauge`, `car`. Theme tokens: `light`, `dark`, `system`; distance tokens: `kilometers`, `miles`; car-list tokens: `list`, `tasks`, `tasksAndHistory`. These map explicitly to the corresponding current enum constants, never ordinal/name inference. Unknown enum token is invalid v1.

`kind` is `backup` or `car`. Group strings and their order are fixed in section 2. UUIDs use the lowercase `8-4-4-4-12` hexadecimal representation; snapshotId is a fresh RFC 9562 UUID v4 for every newly generated artifact, with variant bits `10`. Backup entity IDs preserve existing valid UUIDs. Selective IDs use `c1`, `t1`… and `h1`… in emission order; historical-only `d1`… tokens are assigned in first-reference order after filtering. No plus signs, leading zeroes or alternate case in canonical IDs. References obey the same namespace and ownership rules as section 4.

The sample crypto plaintext in the compatibility proof is a synthetic primitive vector rather than a valid application snapshot; its purpose is checking UTF-8 bytes, framing AAD, ciphertext and tag. Full backup/car payload fixtures are required in step 4. Header limits are byte counts: 16 MiB = 16,777,216; 10 MiB = 10,485,760; 4 KiB = 4,096. The encrypted body always has at least the 16-byte tag. A zero-length/plain invalid payload still fails snapshot validation. Metadata fields use the exact types above; iterations and tagBytes are JSON integers. Empty plain metadata is exactly the two bytes `{}` on emission and no extra object members on decoding.
