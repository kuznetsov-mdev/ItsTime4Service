# Transfer UI and release verification

Updated 2026-11-23 for product revision `db1567d91c5d8a3701057817fc2607cf6fd04c21`. It includes the transfer flow and four RuStore feedback fixes. Independent QA is **BLOCKED** on the iOS 27 confirmed Share to Files scenario. Repeat independent review, minimum-OS checks and user acceptance are open.

## Implemented flow

- Settings and preselected-car entry, full backup, car backup and maintenance schedule export. Selective export starts with all tasks selected. Car backup starts with personal groups and password protection enabled. Schedule export excludes personal groups and starts unprotected. Disabling protection for a personal file requires confirmation.
- Header, size and version checks, password authentication before decrypted metadata, file release and cancellation. Each source car has its own lazy destination list, and a new car's mileage is confirmed before proceeding.
- Separate task, personal-data, related-history and sold-state decisions. The setup step shows concrete task candidates with current intervals and last service. The final preview repeats the chosen target and shows the copy date, active/sold counts, final contents, settings and rollback restrictions.
- Encrypted safety copy or explicit confirmed refusal before replacement, fresh database binding across the native save dialog, final replacement confirmation and atomic application.
- Stale-plan handling, repeat protection and separate notification retry. Back is blocked while applying, including notification synchronization after commit. Back or Done from the result closes the session. Password inputs are scoped to the current step. Unlock clears the field on submit and export preview drops the input fields. File bytes and `SecretBytes` have separate operation cleanup.
- English/Russian/Spanish resources, local units and dates, explicit reveal for hidden VIN/plate.

## Evidence 2026-11-23

| Main-session check on `db1567d` | Recorded result |
|---|---|
| Full Android host suite | 727 tests, 0 failures/errors/skips |
| Full iOS Simulator suite | 709 tests, 0 failures/errors/skips |
| Android APKs | Debug APK and `upgradeCheck` APK built |
| Fresh Android emulator package, 1.0.0 → 1.1.0 | The 1.0.0 app was launched before the first database read and seeded through its UI. A same-package update migrated schema 5 → 7. All legacy columns in cars, tasks, history and settings were preserved, the import entry was reachable and `integrity_check=ok`. |

The source of these recorded results is the [test plan](../../specs/test-plan.md#регрессии-по-ревью-импорта-110). The isolated upgrade runner is in [run_upgrade.py](../../tools/transfer-files-check/run_upgrade.py).

### Independent QA 2026-11-23

| Independent check on `db1567d` | Result |
|---|---|
| Android host suite | Fresh XML results show 727 tests, 0 failures, errors or skips. |
| iOS 18.3.1 Simulator native suite (22D8075) | Fresh XML results show 709 tests, 0 failures, errors or skips. The exact minimum iOS 18.2 was unavailable. |
| iOS 27 system-file XCTest, first matrix | 6/7 passed in `result-20261123-115924-aee43e.xcresult`. `testShareToFilesHasConfirmedCompletion` stopped at `Own run directory is not exposed` before checking confirmation. |
| iOS 27 system-file XCTest, targeted retry | `result-20261123-121910-817cf5.xcresult`, exit 65, zero screen attachments. The system share menu and Files picker opened. The test folder was unavailable, so confirmation and SHA-256 remained unverified. Product failure is unproven. |
| Android API 35 minified system-file harness | Encrypted codec and 6/6 system cases passed, runner exit 0. Coverage includes cancelling pick, save and share, saving and opening plaintext and protected files with SHA-256 checks, and handing off a protected file to the test recipient Transfer Sink with matching SHA-256. |
| Android product UI in the isolated `upgradeCheck` package | Both export purposes were reachable from car details. The car-copy form showed password fields, the selected task and personal groups. The Continue action remained fixed. |
| `TransferExchangeTest` | 3/3 on each platform. Both platforms generated new local payloads and imported retained peer fixtures through SQLite. A fresh manual exchange between installed apps remains outside this run. |

Independent QA verdict for `db1567d` is **BLOCKED**. Both iOS 27 attempts stopped at the test folder before reaching the system Share to Files confirmation. Physical API 24, exact minimum iOS 18.2 and fresh manual end-to-end exchange between installed apps remain open.

## Historical evidence — 2026-11-19 to 2026-11-20

The signed AAB, R8 runtime, system-file matrices and provider exchange below belong to earlier snapshots. The signed AAB details are recorded in the [release note](../releases/1.1.0.md#подписанный-aab-от-19112026). After the export-purpose and add-car entry changes, the preceding run covered 719 Android host tests and 27 iOS wizard tests. After unified destination confirmation, the 20 November run covered 720 Android host and 702 iOS Simulator tests. Those two runs and their build results are detailed in the [test plan](../../specs/test-plan.md#выбор-цели-экспорта-и-импорт-из-формы-авто).

| Check | Result |
|---|---|
| Full Android host suite, 19 November snapshot | 715 tests, 0 failures/errors/skips; workflow d03d426690b465c939b1849acbac5192 sequence35 |
| iOS18.3 native session/document/IO/UI suite | 56 tests, 0 failures/errors/skips; same sequence35 |
| Production iOS simulator/device compilation, test compilation, debug framework | PASS, final sequence35 |
| Native iOS UI/navigation | 9/9 PASS, included in final sequence35; also separately verified in sequence32 |
| Real navigation + Koin + SQLite | Both entry points pass; import applies once and returns to settings |
| Installation update 1.0.0 → 1.1.0 | PASS, isolated unique upgradeCheck package on emulator-5554; schema5→7, car/task/history/settings all preserved column-for-column, integrity_check=ok |
| Android↔iOS real encrypted documents | PASS, sequence23, 3 tests per platform: generated production-provider outputs, peer full-backup replacement and clean-template import through SQLite |
| Signed release AAB | PASS, version1.1.0/code3, expected upload certificate, no INTERNET/storage permissions; see release note |
| R8 system-file runtime | PASS on isolated non-debuggable APK: encrypted codec plus six cancellation/save/open/share cases and fresh recipient SHA256 |
| Final iOS27 system-file matrix | 7/7 PASS, result-20261119-141029-dab736.xcresult; zero screen attachments |
| iOS18.3 system-file matrix | 7/7 PASS, result-20261119-143505-b697c9.xcresult; zero screen attachments |

## Bidirectional files

`TransferExchangeTest`, `TransferPeerFiles` and platform artifact writers retain actual randomly encrypted Android JDK/BC and iOS OpenSSL outputs. The synthetic fixtures exercise a Unicode password. Each peer provider authenticates and decodes the other platform's files. Full restoration compares cars, meaningful order, sold state, deleted-work history, settings, reminder state and valid rollback. Clean-template import requires explicit new-car mileage and keeps unknown last service. Native-dialog save/open/share additionally checks exact SHA-256 against an independently generated CommonCrypto/CryptoKit fixture.

## Test tools and limitations

The upgrade runner validates a unique `upgradeCheck` package. On a fresh install it launches 1.0.0 and waits for the start screen before reading the database. It creates synthetic rows with the real 1.0.0 UI, installs 1.1.0 with `-r` and compares the legacy SQLite columns. Android R8 uses a separate non-debuggable transferReleaseCheck APK signed by the debug certificate. The recipient receipt is reset before each share and checked in the recipient UI.

iOS runs are serialized by a nonblocking runner lock, use explicit boot/DeviceHub setup, per-run folders and bounded watchdogs, and retain their logs/results. The harness opens the native dialogs directly; a separate DocumentsApp launch was removed because it introduced bootstrap failures. Short own-folder names sort first; exact Back labels are distinguished from Actions Menu titles, including iOS18 without a BackButton identifier. Popover cancellation taps outside the popover. All final cited native results must report zero retained screen attachments. No screenshots are opened, exported or published.

Physical API24 performance, memory and cancellation, and the exact minimum iOS18.2 remain pre-release gates. Physical iOS device coverage is still open. See the [release checklist](../releases/1.1.0.md#перед-публикацией).

The R8 runtime check exposed stripped BC reflective registrations: SHA256/AES/PBEPBKDF2 families were listed in usage.txt and absent from mapping.txt. Narrow keep rules for those three families were added to the production release rules; the minified host passed the real encrypted roundtrip and all six file cases; the rebuilt signed AAB passed signature verification. The check host also explicitly registers Kotlin sources and avoids the status-bar overlap of its first button. These host fixes are separate from product UI behavior.

## Acceptance traceability

| Spec criterion | Evidence |
|---|---|
| 1 | TransferExchangeTest: real provider files in both directions, SQLite restoration, active/sold cars, deleted-work history, order and valid rollback |
| 2 | TransferViewModelTest safety/cancellation/stale cases; TransferContentTest final confirm; ApplyImportTest transaction rollback |
| 3 | PrepareImportTest and ApplyImportTest missingGroupDoesNotClear/provenance/explicit targets |
| 4 | ApplyImportTest settingsAreRestoredOnMergeAndKeptOnReplacementWhenDisabled; exchange backup settings |
| 5 | TransferSnapshotTest group/privacy cases, TransferCodecTest; ViewModel group defaults/dependencies |
| 6 | Snapshot/import selection tests, `newHistoryDoesNotInferLastServiceOrCreateUnselectedTasks`; `TransferContentTest.carPickerComposesOnlyVisibleDestinationsAndCanReachTheLastOne` (1,000 destinations) |
| 7 | Destination/mileage validation, missing-group preservation, peer clean-template unknown-service import |
| 8 | `PrepareImportTest.skippingOneMatchDoesNotUndoAnotherMatchUpdate` checks both input orders. `TransferViewModelTest.choosingAnAmbiguousWorkKeepsResolutionOnSetupAndPreviewsTheChosenTarget` and Compose UI tests cover concrete target selection and its final preview. |
| 9 | Related-service/history decision tests; history details absent preserves existing details on update |
| 10 | `TransferContentTest.protectedFileShowsNoContentsAndClearsPasswordOnSubmit` and `leavingExportForPreviewDisposesPasswords`; ViewModel authentication and plaintext confirmation cases |
| 11 | Sold-state planning tests and full-backup exchange with sold-car history |
| 12 | validUnknownPredecessorRollbackSurvivesReplacementAndNewMarksUndo plus peer replacement rollback |
| 13 | repeatedConfirmHasOneResultAndRestartDoesNotRepeat, source identity and operation reuse tests, ViewModel single apply |
| 14 | Common bounded and cancellable I/O tests, historical system-file and R8 checks, and the [independent QA results](#independent-qa-2026-11-23). |
| 15 | ImportNotificationsTest, pending-journal retry and ViewModel scheduler failure/retry without a second import |
| 16 | EN/RU/ES resource parity, distance/date formatting suites, Unicode peer files and fresh schema 5 → 7 installation update. |
| 17 | Source review: no backup scheduler, rotation/deletion of user backups, persistent picker grants or password/key storage |
| U1 | One import entry, kind/groups from authenticated contents, protected preview test and real codec fixtures |
| U2 | Destination/file reset tests; earlier confirmed destinations of other cars survive a later destination edit |
| U3 | Replace UI skips merge decisions; safety cancelled/failed never means SAVED; final summary from bound plan |
| U4 | `TransferViewModelTest.backDuringPostCommitNotificationSyncPreservesImportResult` and `aConfirmedPreviewAppliesOnceAndBackFromSuccessEndsTheSession` |
| U5 | Native cancellation is separate from failure; manifests have no broad storage permissions |

`TransferContentTest.finalBackupPreviewShowsCopyMetadataAndConcreteTaskMapping` covers copy date, active/sold counts and the chosen local task label in the full-backup preview.

### Notes from the 19 November snapshot

Final iOS18.3 diagnosis: the picker was still inside an older run folder (AX title Run-53A73B9B, Actions Menu). Its Back button had the exact label TransferFilesCheck but no BackButton identifier. Matching that exact label distinguishes it from the title/menu, restores parent navigation on iOS18, and produced the complete 7/7 result above. Earlier failed matrices remain historical; two AX UI Snapshot metadata references in one failed run were not exported or opened. The final matrices retain zero screen references.

Final self-review also added actual current/incoming visibility comparisons (including visible defaults for a new car), verified by two UI tests. Confirming unchanged prefilled miles now retains the exact incoming kilometers with the existing keepKm rule; the regression was reproduced as 30400→30401 before the fix and passes on both platforms. EN/RU/ES contain 97 matching transfer strings. These changes pass the final full 715 Android/56 native run (including 9 UI tests), and both platform builds. The final signed artifact is recorded in the release note.

A final fault-injection regression verified protected-file recovery after authentication followed by a transient local-database read failure. Input bytes are now cleared only after destination loading succeeds, so entering the password again works without reselecting the file; release still happens exactly once. The regression failed before the fix (IMPORT_SETUP expected, UNLOCK actual), passes on both platforms and is included in the final 715/56 suite.
