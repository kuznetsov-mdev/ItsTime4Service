# Transfer UI and release verification

Date: 2026-11-19. Current implementation completes the approved 1.1.0 import/export flow and includes the four RuStore feedback fixes. Work is performed by the main session; these are self-checks, not independent review or QA. No publication or new commits are implied.

## Implemented flow

- Settings and preselected-car entry, full backup and selective export; all tasks selected initially, personal groups off, explicit plaintext warning and password confirmation.
- Header/size/version checks, password authentication before decrypted metadata, file release/cancellation, explicit destinations for every source car and confirmed new-car mileage.
- Independent task/personal/related-history/sold decisions, safe defaults, one-to-one concrete candidates and preview of final contents, counters, settings and rollback restrictions.
- Encrypted safety copy or explicit confirmed refusal before replacement, fresh database binding across the native save dialog, final replacement confirmation and atomic application.
- Stale-plan handling, repeat protection, separate notification retry, session termination on success/Back. No password/file/snapshot/decision in routes or saved state; byte and SecretBytes cleanup.
- English/Russian/Spanish resources, local units and dates, explicit reveal for hidden VIN/plate.

## Evidence

| Check | Result |
|---|---|
| Full Android host suite, final product source | 715 tests, 0 failures/errors/skips; workflow d03d426690b465c939b1849acbac5192 sequence35 |
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

`TransferExchangeTest`, `TransferPeerFiles` and platform artifact writers retain actual randomly encrypted Android JDK/BC and iOS OpenSSL outputs. Fixtures are synthetic and use the explicitly test-only Unicode password `Synthetic exchange ñ Тест`. Each peer provider authenticates/decodes the other platform's files. Full restoration compares cars, meaningful order, sold state, deleted-work history, settings, reminder state and valid rollback. Clean-template import requires explicit new-car mileage and keeps unknown last service. Native-dialog save/open/share additionally checks exact SHA-256 against an independently generated CommonCrypto/CryptoKit fixture; these are complementary checks.

## Test tools and limitations

The upgrade runner never targets the normal application: it validates a generated upgradeCheck package, creates synthetic data with the real 1.0.0 UI, installs 1.1.0 with -r, and compares all legacy SQLite columns. No app data is cleared. Android R8 uses a separate non-debuggable transferReleaseCheck APK signed by the debug certificate; the recipient receipt is reset by writing an empty value before each share, then checked via its own UI rather than run-as. Test-host Kotlin sources must be registered as Kotlin sources, not Java sources.

iOS runs are serialized by a nonblocking runner lock, use explicit boot/DeviceHub setup, per-run folders and bounded watchdogs, and retain their logs/results. The harness opens the native dialogs directly; a separate DocumentsApp launch was removed because it introduced bootstrap failures. Short own-folder names sort first; exact Back labels are distinguished from Actions Menu titles, including iOS18 without a BackButton identifier. Popover cancellation taps outside the popover. All final cited native results must report zero retained screen attachments. No screenshots are opened, exported or published.

Physical API24 performance/memory/cancellation and exact minimum iOS18.2 are not verified. These remain explicit pre-release gates. No minimum-OS success or completed publication is claimed. The existing format v1, migration lineage, offline policy and dependency pins remain unchanged.

The R8 runtime check exposed stripped BC reflective registrations: SHA256/AES/PBEPBKDF2 families were listed in usage.txt and absent from mapping.txt. Narrow keep rules for those three families were added to the production release rules; the minified host passed the real encrypted roundtrip and all six file cases; the rebuilt signed AAB passed signature verification. The check host also explicitly registers Kotlin sources and avoids the status-bar overlap of its first button. These host fixes are separate from product UI behavior.

## Acceptance traceability

| Spec criterion | Evidence |
|---|---|
| 1 | TransferExchangeTest: real provider files in both directions, SQLite restoration, active/sold cars, deleted-work history, order and valid rollback |
| 2 | TransferViewModelTest safety/cancellation/stale cases; TransferContentTest final confirm; ApplyImportTest transaction rollback |
| 3 | PrepareImportTest and ApplyImportTest missingGroupDoesNotClear/provenance/explicit targets |
| 4 | ApplyImportTest settingsAreRestoredOnMergeAndKeptOnReplacementWhenDisabled; exchange backup settings |
| 5 | TransferSnapshotTest group/privacy cases, TransferCodecTest; ViewModel group defaults/dependencies |
| 6 | Snapshot/import selection tests, newHistoryDoesNotInferLastServiceOrCreateUnselectedTasks, explicit target UI |
| 7 | Destination/mileage validation, missing-group preservation, peer clean-template unknown-service import |
| 8 | PrepareImportTest matching/update/separate/duplicate-target cases; candidate intervals and last service in UI |
| 9 | Related-service/history decision tests; history details absent preserves existing details on update |
| 10 | Missing/identity/provenance conflict tests; ViewModel displayed default SKIP resolves without an extra radio tap |
| 11 | Sold-state planning tests and full-backup exchange with sold-car history |
| 12 | validUnknownPredecessorRollbackSurvivesReplacementAndNewMarksUndo plus peer replacement rollback |
| 13 | repeatedConfirmHasOneResultAndRestartDoesNotRepeat, source identity and operation reuse tests, ViewModel single apply |
| 14 | Common bounded/cancellable IO tests; real system matrices and R8 crypto/files checks; see final evidence table |
| 15 | ImportNotificationsTest, pending-journal retry and ViewModel scheduler failure/retry without a second import |
| 16 | EN/RU/ES resource parity, distance/date formatting suites, Unicode peer files, real schema5→7 installation update |
| 17 | Source review: no backup scheduler, rotation/deletion of user backups, persistent picker grants or password/key storage |
| U1 | One import entry, kind/groups from authenticated contents, protected preview test and real codec fixtures |
| U2 | Destination/file reset tests; earlier confirmed destinations of other cars survive a later destination edit |
| U3 | Replace UI skips merge decisions; safety cancelled/failed never means SAVED; final summary from bound plan |
| U4 | Result has only finish/notification retry; Back closes session and old confirmation cannot apply again |
| U5 | Native cancellation is separate from failure; manifests have no broad storage permissions |

These checks establish software readiness on the tested environments. They do not replace the explicitly outstanding physical API24 and exact minimum-iOS checks or user acceptance.

Final iOS18.3 diagnosis: the picker was still inside an older run folder (AX title Run-53A73B9B, Actions Menu). Its Back button had the exact label TransferFilesCheck but no BackButton identifier. Matching that exact label distinguishes it from the title/menu, restores parent navigation on iOS18, and produced the complete 7/7 result above. Earlier failed matrices remain historical; two AX UI Snapshot metadata references in one failed run were not exported or opened. The final matrices retain zero screen references.

Final self-review also added actual current/incoming visibility comparisons (including visible defaults for a new car), verified by two UI tests. Confirming unchanged prefilled miles now retains the exact incoming kilometers with the existing keepKm rule; the regression was reproduced as 30400→30401 before the fix and passes on both platforms. EN/RU/ES contain 97 matching transfer strings. These changes pass the final full 715 Android/56 native run (including 9 UI tests), and both platform builds. The final signed artifact is recorded in the release note.

A final fault-injection regression verified protected-file recovery after authentication followed by a transient local-database read failure. Input bytes are now cleared only after destination loading succeeds, so entering the password again works without reselecting the file; release still happens exactly once. The regression failed before the fix (IMPORT_SETUP expected, UNLOCK actual), passes on both platforms and is included in the final 715/56 suite.
