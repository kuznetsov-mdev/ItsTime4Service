# Transfer snapshot verification

Date: 2026-11-10. Stage 3 of the [implementation plan](../superpowers/plans/2026-11-09-export-import.md); normative [wire/domain contract](../../specs/transfer-format.md).

## Scope

Implemented the domain snapshot/selection/outcome types and read-only TransferRepository port, SQLDelight snapshot repository/mapper and Koin bindings. Reads use one transaction and the existing database dispatcher. Car/task/history cursors stop with an explicit limit failure, after task selection; reminder/ownership cursors do not accumulate unrelated rows. No schema migration is required for the two added queries. Settings.DEFAULT is shared with the existing settings repository.

Selective snapshots use file-local IDs and exclude undeclared personal groups. Full backups retain sold cars, hidden original values, independent unknown service facts, ordering, dangling historical task references, explicit rollback predecessors, settings and last-shown reminder traces. Reminder conversion captures one clock/time zone, handles DST using kotlinx.datetime policy and floors timestamps to milliseconds. No source data is written or inferred from history. Cancellation propagates; safe failures contain fixed reason codes only.

## Executed checks

All commands used standalone gradle-run workflow `6c953a6291dfca30e1025f6d08cabc00`; full managed logs were not reopened. Questions and bounded results:

| Sequence | Question | Nested Gradle tasks and result |
|---|---|---|
| 7 | «Фиксируют ли новые тесты ExportSelection отсутствующие контракты перед реализацией этапа 3?» | `:shared:testAndroidHostTest --tests '*ExportSelectionTest'`; expected RED: unresolved new domain types, exit 1 |
| 8 | «Фиксируют ли SQLite-тесты снимка отсутствующий TransferRepositoryImpl перед реализацией?» | `:shared:testAndroidHostTest --tests '*TransferSnapshotTest'`; expected RED: unresolved repository implementation, exit 1 |
| 9 | «Проходят ли ExportSelectionTest и TransferSnapshotTest после реализации согласованного снимка SQLite?» | Both targeted Android test classes, exit 0, 16.425s |
| 10 | «Проходят ли границы 20000 работ/50000 записей, privacy и DST-сценарии снимка на Android и компилируются ли iOS production/test?» | Targeted Android tests plus `:shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64`; exit 0, 17.149s |
| 11 | «Проходит ли полный Android host suite после этапа 3 без регрессий форм, настроек, истории, миграций и crypto proof?» | `:shared:testAndroidHostTest`; exit 0, 36.060s; XML total 545 tests, 0 failures, 0 errors, 0 skipped |
| 12 | «Проходят ли 23 теста состава/снимка и 7 crypto proof на нативной SQLite iOS, с компиляцией production/test для физического iPhone?» | `:shared:iosSimulatorArm64Test --tests '*ExportSelectionTest*' --tests '*TransferSnapshotTest*' --tests '*TransferCryptoIosProofTest*' --device 8471AC1C-FEE2-462B-8FE1-3EE4F6BBB36D :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosArm64`; exit 0, 34.220s; XML 5 + 18 + 7 tests, 0 failures/errors/skips |

The iOS run used iOS18.3; no physical iPhone execution is claimed. Final summaries have no warnings or unresolved failures. Source formatting was checked with `git diff --check`.

## Coverage and remaining work

The 23 new tests cover all group switches, history/details dependency, absence versus explicit null, empty selection, selected IDs/order, stable equal-date history, sold/deleted-task history, rollback with unknown predecessor, hidden source fields/confirmed description, effective defaults/settings, cross-car links, overflow, UTF-8 limits, 1001/20001/50001 failures and valid count boundaries, failure/cancellation/transaction, reminder cutoff/duplicates/stale links, source zones in both UTC directions, DST gap/overlap and sub-millisecond flooring.

This completes logical snapshot creation, not the feature. Serialization/framing and exact encoded file-size/memory limits are stage 4; atomic import/provenance are stage 5; filenames/native file handoff are stage 6; UI is stage 7. Runtime crypto/JSON dependencies remain absent at this checkpoint. The user explicitly moved real API24 execution and measurement to mandatory pre-release validation. Minimum-OS timing/memory, actual saved-file exchange and release checks remain unverified.
