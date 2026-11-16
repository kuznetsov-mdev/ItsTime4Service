# Transfer import verification

Date: 2026-11-16. Stage 5 of the [implementation plan](../superpowers/plans/2026-11-09-export-import.md). Product requirements: [export/import](../../specs/export-import.md); normative [format and operation contract](../../specs/transfer-format.md).

## Implementation

Implemented the pure multi-car import planner, explicit concrete task selection, independent personal/history decisions, related service/history components, sold-state consequences, exact preview/summary, versioned typed SHA-256 fingerprints, atomic SQLite apply, persisted source identity/provenance, operation journal and notification reconciliation. Schema 7 adds three bookkeeping tables and nullable exact `planned_reminders.shown_at_utc` through migration `6.sqm`; old user rows/order and the 5→6 unknown-service migration are retained. No new dependencies or wire-format change.

All work was executed by the main session; checks below are self-verification, not an independent review/QA. No commit, push, publication, screenshots, secret-file read or access to the user's application database occurred. SQLite tests use isolated synthetic fixtures.

## Executed checks

Gradle commands use standalone `gradle-run` workflow `bf88bf0d77a85cebe4210a95e32deada`; bounded summaries and numeric/synthetic test XML diagnostics only. Logs are retained: finish was not called because it removes files and deletion has not been authorized.

- Initial expected RED: missing ImportPlanner/ImportPreparation (sequence 2); first sandbox attempt could not open the Gradle distribution lock, then the wrapper was rerun with approved cache access.
- Sequences 3–4: initial planner and migration tests PASS, including schema generation.
- Sequence 5: a test inferred FixedOffsetTimeZone instead of TimeZone; corrected the declared test type. Sequence 6: atomicity/idempotence/provenance/stale-plan cases PASS.
- Sequences 7–8: expanded conflict/settings/fingerprint/migration cases PASS.
- Sequence 9: one fixture incorrectly reused a backup snapshot ID for a different selective payload; source identity validation rejected it. Assigned a fresh synthetic snapshot ID. Sequence 10: the full targeted matrix including cancellation/notification recovery/AppViewModel PASS.
- Sequences 11–12: native compilation exposed JVM-only map sorting/putIfAbsent calls. Replaced them with common sorted entries/getOrPut. Sequence 13: targeted Android tests and production/test compilation for iosSimulatorArm64 and iosArm64 PASS, no warnings.
- Sequence 14: 59 native iOS tests (planner 21, atomic apply 21, fingerprints 5, notification recovery 4, migrations 8), 0 failures/errors/skips, PASS in 102.937s; no warnings. Three additional assertions (independent snapshot digest vector, merged aggregate limit and defensive preview) were added afterward and are included in final validation below.
- Sequence 15: full Android 629/0/0/0 and both iOS production/test compilations PASS in 61.616s, no warnings. Sequence 16: native iOS 62/0/0/0 PASS in 30.142s, no warnings.
- Sequence 17 (additional required timezone audit): RED reproduced a real DST bug, expected 06:30Z but got 05:30Z for the second New York overlap hour. A naive-local timestamp cannot preserve the represented instant. Added nullable `shown_at_utc`, carried it via ReminderRecord/normal reconciliation and shared the collector between snapshot and import capture. Old rows retain their conversion policy. Sequence 18: targeted import/snapshot/notification/migration/repository tests PASS in 27.154s.
- Final sequence 19 after the UTC fix: full Android **634 tests, 0 failures/errors/skips**, production/test compilation for **both iOS architectures PASS**, no warnings, 71.958s. Final sequence 20: **106 native iOS tests, 0 failures/errors/skips**, no warnings, 38.030s. Suite breakdown: planner 21, apply 24, fingerprints 6, notifications 5, collector 3, migrations 8, existing snapshots 18, existing repositories 16, existing sync 5. The stage adds 60 Android cases over the prior 574 baseline (including the new migration case).
- `git diff --check` PASS. Wrapper status: completed, sequence 20 exit 0. Stage 5 is Ready for acceptance; user acceptance is pending.

## Regression coverage

- Every incoming car is explicitly assigned; brand/VIN/name alone never merge cars or grant links. Multiple same-name task candidates require a concrete update target; stale, cross-car and duplicate update targets cannot apply. Skipping parameters can accept service separately; rejecting ambiguous personal facts does not force a task mapping.
- Absent groups preserve current data; nullable incoming facts need acceptance. History does not infer servicing or create excluded tasks. Independent history conflicts have separate outcomes; only connected current service/history facts are grouped. Changing cost alone preserves valid rollback; conflicting service invalidates old rollback while retaining records. Unknown predecessor and later normal marks/unmarks are covered.
- Replace requires safety and ignores merge decisions; restores order and complete data. Merge preserves existing positions and appends new entities in file order. Multi-car merge succeeds together; failure on a later car rolls back earlier writes.
- Database/source/decision/change fingerprints bind preview/apply. Raw future reminder, timezone and provenance changes invalidate stale plans. Map/set insertion order does not affect logical hashes, while array order, absent/null/zero and text boundaries remain distinct. Both providers must match SHA-256 and an independent Python versioned snapshot vector.
- Repeated operation ID returns its stored result, including after restart/replacement; altered reuse fails. Same snapshot ID with changed content fails. Reopening uses provenance without creating automatic copies; explicit separate instances remain distinct. Deleted mappings require a new decision.
- Error and cancellation before commit roll back user rows, provenance and journal; cancellation after commit leaves recoverable pending state. No scheduler operation executes inside a DB transaction. Notification failures/marker write failure return committed data with retry state; scheduler-only retry and restart recovery do not reimport.
- Migration paths 5→7 and 6→7 preserve unknown/zero, row order, links, hidden flags, descriptions, settings and old eligibility. Global merged limits are checked before writes; preview/request mutations cannot change the prepared writes.

## Remaining gates

Stage 6: native picker/save/share, bounded read and file cancellation/denial/space handling. Stage 7: Compose operation/password lifecycle, real confirmation/safety-save flow, actual Android↔iOS saved-file exchange and end-to-end acceptance. Version remains 1.0.0 (2); this is internal implementation for 1.1.0, not a published release.

Physical API24/minimum iOS, KDF/max-memory/cancellation timing, minified runtime exchange and distribution delta remain release gates. Existing naive-local reminder records remain an approximation of shown notifications. New imported exact traces retain their UTC instant across receiver timezone changes and the second DST-overlap hour, including a regular scheduler rewrite. Neither kind is proof of actual delivery by the OS.


## Wrapper questions and bounded answers

| Sequence | `--question` | Bounded answer |
|---|---|---|
| 1–2 | Do the initial import planner tests expose the missing implementation? | Sandbox distribution lock denied; approved rerun produced expected missing-planner RED |
| 3 | Do planner safety, target and unknown-service tests pass? | PASS |
| 4 | Do the import planner and schema migration compile and pass targeted tests? | PASS |
| 5–6 | Do atomic import, idempotence, provenance and stale-plan tests pass? | Test timezone type corrected, then PASS |
| 7 | Do expanded import conflict, atomicity and migration cases pass? | PASS |
| 8 | Do import planner, fingerprints, atomic writes and migration tests pass together? | PASS |
| 9–10 | Do complete stage 5 cases including cancellation and notification recovery pass on Android? | Invalid reused fixture snapshot ID corrected, then PASS |
| 11–12 | Does stage 5 compile for both iOS architectures including test sources? | Exposed JVM-only sorted-map/putIfAbsent calls; corrected to common Kotlin |
| 13 | Do final import conflict rules and notification recovery pass on Android and compile on both iOS architectures? | PASS, no warnings |
| 14 | Do import data and conflict-component tests pass natively on iOS? | PASS, 59/0/0/0 |
| 15 | Does the complete Android host suite pass with stage 5 and do both iOS production and test compilations remain clean? | PASS, 629/0/0/0; no warnings |
| 16 | Do all 62 import and migration tests, including the final three boundary cases, pass natively on iOS? | PASS, 62/0/0/0; no warnings |
| 17 | Does an imported shown-reminder instant survive the second DST-overlap hour and a receiver timezone change? | Expected regression RED: 06:30Z became 05:30Z |
| 18 | Are exact imported reminder instants preserved across DST and timezone changes without breaking legacy snapshot and reminder behavior? | PASS |
| 19 | Does the full Android suite and both iOS production/test compilations pass after preserving exact UTC reminder traces? | PASS, 634/0/0/0; no warnings |
| 20 | Do native iOS import, UTC/DST, legacy snapshot, migration and reminder repository tests pass after the exact-instant fix? | PASS, 106/0/0/0; no warnings |

Final nested aggregate command: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosArm64`.

Final native command: `./gradlew :shared:iosSimulatorArm64Test --tests '*PrepareImportTest*' --tests '*ApplyImportTest*' --tests '*ImportFingerprintTest*' --tests '*ImportNotificationsTest*' --tests '*TransferReminderCollectorTest*' --tests '*MigrationTest*' --tests '*TransferSnapshotTest*' --tests '*SyncRemindersUseCaseTest*' --tests '*RepositoriesTest*'`.

Both were executed only through the managed wrapper, not directly.


## Commit preparation

The user authorized `data: Add atomic transfer import` on 2026-11-16. The main session verified the current wrapper result (completed, sequence 20 exit 0), current XML totals (634 Android / 106 native iOS, zero failures/errors/skips), whitespace and all 42 candidate paths. Credential-pattern scan reported zero matches and zero forbidden paths without displaying source values. Production/test/build inputs have not changed since sequences 19–20; only documentation records the authorization. Existing test evidence was reused without an unnecessary build repeat. This is a local commit; push/publication are not authorized.
