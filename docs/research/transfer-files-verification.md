# Transfer file adapters — paused before system acceptance

Stage 6, 2026-11-17. Main session works independently; no agents, commit or publication. [Plan](../superpowers/plans/2026-11-09-export-import.md), [contract](../../specs/transfer-format.md).

## Current implementation

Semantic TransferFiles pick/read/save/share/release port; non-serializable opaque handles; distinct Success/Cancelled/Failure; one lifecycle-owned operation/dialog; close/cancellation-aware bounded IO (16 MiB, untrusted size hints, short reads/writes, flush/close errors). Common state contains no URI/URL. Android SAF, ephemeral grants, narrow cache FileProvider and chooser-selected-target acknowledgement; iOS document open/export-as-copy, security scope, file coordinator and activity completion/popover. Root platform hosts and DI are connected; product transfer screens remain stage 7.

Own private UUID staging directories are eligible for cleanup after 24 hours, at the next file operation. No picked/saved user file is deleted. Android files exposed to the chooser are retained for the receiver, even if the caller is later cancelled; iOS successful/cancelled export staging is discarded after the acknowledged result, while handed-off/interrupted staging follows retention. Caches are private and excluded from OS backup. No broad or persistable storage permissions and no bookmarks are added.

## Verification ledger

Gradle workflow `29e49353d9f446c0f3398a8b16c2e450`; all Gradle commands use the compact-output wrapper, raw logs are not reopened. No finish/deletion is authorized.

1. Expected RED: missing bounded IO/dialog implementation.
2. Initial compile errors: generic continuation inference, NSObject import and Foundation nullable references; corrected from bounded diagnostics.
3. Shared tests and both iOS production compilations PASS; fixed explicit BetaInteropApi opt-in, unnecessary assertions and platform File nullability warnings.
4–5. Corrected NSDate factory API and test-only ApplicationProvider dependency by using existing Robolectric RuntimeEnvironment.
6. Test ContentProvider lacked attachInfo authority; initial three provider tests correctly failed. Registered its synthetic authority.
7. All targeted Android common/provider/fixture tests, both iOS production/test compilations, isolated Android transferCheck APK and debug simulator framework PASS without warnings.
8–10. Standalone Kotlin/Native stream tests revealed NSFileCoordinator read accessor not invoked: Cocoa error 256, fixture exists, NSBundle.mainBundle has no bundle identifier. An explicit purposeIdentifier did not alter it. The fixture exists; the standalone process is unbundled. Test-only embedded Mach-O metadata made bundled=true but Cocoa 256 persisted, so missing bundle ID alone is not the cause. Investigate the executable/registered-app host boundary rather than weaken external-file coordination. Sequence 12 proved the cause: KGP defaults to `simctl spawn --standalone`; with `standalone=false` and a fully booted simulator, all five Foundation stream/error/security-scope cases pass. The build now boots the selected simulator before native tests; no coordinator fallback or weakened expectation was added.

12. Native file tests and instrumented simulator framework PASS in a fully booted simulator.
13–14. Temporary Foundation diagnostic notification argument required escaping Kotlin `object`; corrected, framework PASS.
15. Full Android host suite **655/0/0/0**, both iOS production/test compilations and debug/transferCheck APKs PASS without Gradle warnings.

16. Extra buffer clearing and cancellation tests PASS, simulator production/test compilation PASS.
17. Final code without temporary diagnostic hooks: full Android **659/0/0/0**, both iOS production/test compilations, debug simulator framework and debug/transferCheck APKs PASS without Gradle warnings.
18. All native iOS file/lifecycle/cancellation/fixture/security-scope tests **26/0/0/0**, PASS without warnings.

19. A new negative test reproduced Android FileNotFoundException masking an underlying ENOSPC/EACCES cause.
20. Classifier corrected to inspect native causes before returning missing-file fallback: full Android **660/0/0/0**, debug/transferCheck APKs PASS without warnings. iOS/common production sources unchanged since 17–18.

## Real-system evidence so far

Android API35 read-only emulator, isolated `.transfercheck` app, own synthetic recipient, no screenshots. The repeatable runner now wakes its own emulator, clears only its task stack, drains nested folders before cancellation, recognizes intentresolver, ignores failed/stale UIAutomator dumps and uses a fresh directory created by DocumentsUI. `run_android.py` passed all six cases initially and again on the final IO code (before the subsequent classifier-only ENOSPC/EACCES fix): cancel pick/save/share, system save/open Plain.it4s and Protected.it4s with identical SHA-256, protected chosen-target handoff with identical recipient hash. Fixture sizes 325/485 bytes. The encrypted fixture was independently generated via Apple CommonCrypto/CryptoKit; real provider + codec test authenticates it. Fixed test-only salt/nonce/password are never used by production providers.

iOS18.3 isolated `.filescheck` app, separate generated Xcode project. Initial harness accidentally built x86_64 too; fixed to arm64, matching the KMP framework. Picker cancellation passed when XCTest ran alone. A full 6-case run had 1 pass/5 failures at native dialog selectors; bilingual selectors and bounded known-button diagnostics are being checked. One diagnostic enumeration itself raced a changing accessibility snapshot; replaced index enumeration with fixed known-label queries and adaptive 30-second waits. Bounded phase diagnostics subsequently confirm staging completes and dialog launch is reached. The remaining investigation is native presentation/accessibility, not byte staging. No native save/share success is claimed yet. Do not run native Gradle tests and XCTest concurrently against the same simulator.

## Primary platform references

- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): user-selected document access and create/open actions, without wide storage permissions.
- [Android chooser callbacks](https://developer.android.com/reference/android/content/Intent#EXTRA_CHOSEN_COMPONENT_INTENT_SENDER): selection acknowledgement is not guaranteed on abandonment and is not receiver persistence/delivery.
- [Apple document picker](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller): security-scoped resources and coordinated external-document access; export-as-copy leaves the staging source local.
- [Apple activity completion](https://developer.apple.com/documentation/uikit/uiactivityviewcontroller/completionwithitemshandler-swift.property): completion/cancellation is determined after the activity ends, not when the sheet first appears.

## Remaining work

Resolve native coordination test-host handling with honest hosted integration evidence; finish actual iOS cancel/export/open/share, optionally exchange the saved encrypted fixture between both check apps; then full Android suite/debug APK, native tests and both iOS production/test compilation. Update specs/test plan/dev_plan/session. At that stop point, no stage-6 commit had been authorized. Physical API24/minimum iOS, final product screens/password lifecycle, full Android↔iOS import acceptance and release checks remain later gates.


## Stop point — 2026-11-17

User ended work for today. No active task-specific Xcode process remains; Gradle workflow is completed, sequence 20 exit 0. Implementation stays uncommitted; no push/publication or transfer UI stage 7 work.

Final evidence: Android host **660/0/0/0**, native iOS file tests **26/0/0/0**, both iOS production/test compilations, simulator framework and APKs PASS. Android API35 system runner **6/6 PASS** on final IO code, before the subsequent classifier-only fix (covered by the full suite). The runner was repaired for nested Back navigation, separate per-run DocumentsUI-created directories, fresh snapshots, task stack/wakeup and the `com.android.intentresolver` chooser package.

iOS18.3 and a new isolated iOS27 device reproduced export failure even in a pure UIKit control. The full iOS27 run had 2 passes (pick/share cancellation) and 5 failures; export/open and confirmed share remain unverified. Signing the harness ad-hoc did not yield a passing control: XCTest reported `kAXErrorServerNotFound`; the following bounded run timed out during app launch. Added proper UIWindowScene, explicit simulator selection, zero-test failure, bounded test execution and Files-provider warmup. DeviceHub was successfully opened from `/Applications/Xcode.app/Contents/Applications/DeviceHub.app` after the timed-out run; the GUI-attached case has not been rerun because the user stopped work.

Next: inspect app launch/accessibility on the isolated device with DeviceHub attached, establish a passing pure UIKit control, then run the six adapter scenarios and inspect real saved bytes. Do not repeat broad Gradle validation without source changes. The stage is **not Ready for acceptance**. Keep the remaining physical/minimum OS and product UI/release gates explicit.

Xcode linking also warns that prebuilt OpenSSL object files were built for iOS-simulator 26.5 while the check host deployment target is 18.0. This is separate from successful Gradle compilation and needs minimum-OS compatibility verification before release; the pure UIKit control does not prove runtime crypto compatibility on the minimum OS.

## Continuation — 2026-11-18

The user resumed work after reporting successful RuStore publication. The main session worked independently. Initial changes concerned the isolated Swift/XCTest host, Python runner and documentation. An experimental explicit `pageSheet` presentation was tested and then reverted: the cancellation gesture started on the folder title, opening a preview instead of dismissing the sheet. The final gesture starts above the navigation title. No production picker-presentation fix is claimed. Wire v1, dependencies and database are unchanged. Common UI feedback fixes are tracked separately.

The temporary Xcode project was regenerated and the dedicated simulator booted with DeviceHub open. Text hierarchy diagnostics exposed several harness errors: returning from Files did not reliably restore input focus; a stale `Other` named Cancel overlapped More; the app and document picker both exposed Save; Recents and Browse had different container identifiers; tapping a filename label did not select the document; Save to Files was not necessarily a Button. The host now relaunches into the foreground after Files warmup. Tests scope save actions to native navigation bars, wait for the actual navigation UI, select file cells, accept the system share action across accessibility types, and cancel export with the sheet-dismiss gesture. Saved/shared files have per-run output names. The protected share test reopens the saved file and checks its SHA-256 after the completion callback.

On iOS **18.3.1**, simulator **iPhone 16**, all **six adapter scenarios passed** in `result-20261118-120350-5e1411.xcresult`: cancel pick/save/share; save/open/read Plain and Protected; confirmed protected Save to Files followed by open/read. Their saved/read SHA-256 values match the independent fixtures (325 and 485 bytes). A separate read-only inspection of the isolated test app's Documents directory independently confirmed both sizes and hashes. That full run had **6 passes and 1 failure**: its remaining failure was the auxiliary pure UIKit control, not an adapter scenario. After using a temporary writable fixture and an explicit sheet presentation, the control also passed separately in `result-20261118-121125-056e69.xcresult` (**1/0/0**). It requires an actual cancellation callback; no success is inferred from a tap. The control does not use the production adapter. The host retains its synthetic temporary fixtures.

The initial attachment configuration used the wrong key: successful tests alone did not reveal automatic captures on failure. The corrected generated test plan uses **`uiTestingScreenshotsLifetime=keepNever`** and **`userAttachmentLifetime=keepNever`**. The runner checks result attachment metadata and rejects retained screenshots/recordings/snapshots. Both a failed cancellation run (`result-20261118-115845-4cc7de.xcresult`) and the six-pass run above report **zero screen attachments**. Earlier statements about screenshot-free XCTest runs did not account for automatic failure attachments and are superseded by this explicit verification. No captures are exported or published.

The first corrected iOS27 matrix (`result-20261118-121313-1254d7.xcresult`) passed native control, cancel pick/share and both save/open/read cases, but failed cancel save and finding Save to Files. An experimental `pageSheet` adapter run passed cancel-save on iOS27 (`result-20261118-125725-4b1555.xcresult`, **1/0/0**, zero screen attachments).

Fresh Gradle workflow **`f5c4d4e391b333b46707a18a7ab54aee`**: sequence 1 answered “Собирается ли iOS после pageSheet?” — PASS, production/test compilation for iosSimulatorArm64 and iosArm64 plus debug simulator framework; sequence 2 answered “Проходят ли файловые тесты iOS?” — PASS, **26/0/0/0**, confirmed from all six XML suites. Both runs completed with exit 0 and no warning fingerprints. These results concern the intermediate presentation experiment. The restored presentation and subsequent common UI changes require fresh validation; the earlier Android counts are historical. Wrapper logs are retained; finish was not called because file deletion is not authorized.

The physical API24/minimum-iOS, minimum-OS crypto/linker, product transfer UI and full Android↔iOS import acceptance remain later release checks. An intermediate iOS27 full run passed 7/7 (`result-20261118-133615-77e52c.xcresult`); the matching iOS18 run passed 4/7 (`result-20261118-133615-34712f.xcresult`). The failing gesture opened a folder-title preview. Both retained zero screen attachments. Final matrices with the corrected gesture and restored production presentation are pending.

## Latest harness evidence — 2026-11-18

The gesture above the navigation bar hit the status-bar area. The bounded UIKit control measured a navigation bar `(0,69,393,108)` and stale Cancel `(283,75,47.7,44)`. A zero-hold drag from the right edge of the bar now cancels the native control: `result-20261118-142537-fc5ba5.xcresult`, **1/1**, zero screen attachments. Native Save actions now require `isHittable` before tapping. No production picker presentation change remains.

Latest full iOS18 result `result-20261118-142701-016a3b.xcresult`: **5/7**, zero screen attachments. Cancel-save, cancel-share, native control, plain save/read and protected save/read passed. The pick scenario had a bootstrap failure; confirmed share reached its completion but could not find the expected file cell on reopen. This does not replace the earlier separate successful share/hash evidence with a claim of a stable matrix.

iOS27 `result-20261118-141928-c510c7.xcresult` was interrupted with SIGINT after a bootstrap stall; `result-20261118-142701-e4d183.xcresult` failed before the test connected (runner received TERM). Both report zero screen attachments. A subsequent targeted shutdown found the dedicated simulator already Shutdown; an app-container query also found the iOS18 device unavailable. Next investigation must establish explicit boot/foreground state and use sequential device runs before diagnosing adapter code. All task-owned Xcode runs have ended.

Fresh final-source Gradle sequence16 (workflow `f741798cc226b9da005fe62b747d3901`): «Проходят ли файловые тесты iOS?» — PASS, **26/0/0/0**. Updated form UI sources and tests pass the independent full Android **684/0/0/0**, both iOS production/test compilation, framework and debug APK checks described in [the form report](form-input-feedback-verification.md). File adapter stage6 remains **not Ready for acceptance**. The user later explicitly authorized committing the implementation and harnesses; no push/publication.

## Commit verification — 2026-11-18

The user authorized two logical commits: production adapters/core IO tests, then fixtures and isolated native-dialog verification tools. Both snapshots were checked separately. No additional system UI runs were performed for this commit request; earlier incomplete iOS evidence and release gates remain in force.

Снимок коммита 1: Android **683/0/0/0**, native iOS file tests **25/0/0/0**, Android APK и обе iOS production/test compilation PASS. Workflow `e20fda31be0b5a5606614f7d71edec81`, «Проходит ли файловый коммит 1?».

Снимок коммита 2: Android **684/0/0/0**, native iOS file tests **26/0/0/0**, Android APK и обе iOS production/test compilation PASS. Workflow `e20fda31be0b5a5606614f7d71edec81`, «Проходит ли файловый коммит 2?».
