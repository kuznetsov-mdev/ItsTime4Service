# Form feedback verification — 2026-11-18

The user requested four corrections after RuStore publication: multiline comments, calendar date selection, scrolling to the first missing required field on save, and upcoming/total counters in compact car rows. Work was performed by the main session in the existing branch. Pre-existing transfer changes are retained separately; the user subsequently authorized four logical commits for these feedback fixes; unfinished transfer adapters remain outside the series.

## Behavior

- `LabeledMiniField` uses IME Default for multiline content; Enter inserts a newline. Single-line Done still submits.
- `AppDateField` is a calendar button without editable semantics. All six service/history/filter dates use it. Selection is confirmed explicitly; cancel preserves the input, clear empties it. Leap days keep the existing RU/ES/EN digit representation; conversions use UTC dates. The calendar adapts to narrow and short windows, with scrolling inside the dialog. Both filter bounds have accessible names.
- Rejected saves advance a validation request. The screen reveals the first required field in display order; editing alone does not scroll. Car brand/mileage take priority over task blocks. Broken collapsed blocks expand. Repeated rejected saves reveal the field again.
- Compact `GaugeRing` displays `alertCount/taskCount`, including `0/0`. Both values already came from reactive repository data. A real SQLite integration verifies `1/1 → 1/9 → 1/1 → 0/0` after later task creation/deletion.

## Evidence

The original combined-working-tree Gradle checks ran through `skills/gradle-run/scripts/gradle_run.py`, workflow `f741798cc226b9da005fe62b747d3901`. Managed full logs were not opened or removed.

Question **«Проходят ли проверки форм?»** — **PASS** on final sequence15:

```
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosArm64 :shared:linkDebugFrameworkIosSimulatorArm64
```

Exit0, no warning fingerprints. Initial Android XML totals: **679 tests, 0 failures/errors/skips**. After the user explicitly requested checking every date field, sequence18 reran the full host suite: **684 tests, 0 failures/errors/skips**. Sequence17 answered **«Все ли поля дат открывают календарь?» — PASS**, including compilation of all updated test sources for both iOS targets. Debug APK exists at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Both iOS production/test compilations and the simulator framework passed. This is Android Compose/Robolectric behavior verification and iOS compilation; no claim of iOS form UI acceptance is made.

Focused checks include `ServiceRecordFieldsTest`, `DateFieldsTest`, `HistoryDialogsTest`, form ViewModel/screen tests, `TaskFieldsTest`, car screen/ViewModel tests and `CarsTaskCountsIntegrationTest`. They cover actual Enter, leap-day confirmation in three formats, cancellation/clearing, keyboard-free date input, first-error visibility on a small screen, repeated saves, collapsed blocks, and later task updates.

Initial RED confirmed the old comment used IME Done. Calendar selectors were corrected to full-date text semantics; the dialog was adapted to the small test window. The first broad run found two old `AppFlowsTest` assumptions that dates remained editable inputs. The filter now addresses the first remaining input; the new-record flow scrolls to the date button and selects a fixed month/year/day using the calendar. Final full suite passed after these test updates. No failing test was skipped or deleted.

`git diff --check`, inline JavaScript syntax, Python harness syntax and all three resource XML files passed inspection. This is self-verification, **Ready for acceptance**, pending the user's acceptance. No screenshot tests were used.

## Complete date-field inventory

| Form | Field | UI evidence |
|---|---|---|
| New car, task block | Last service date | `CarFormScreenTest.taskBlockDateOpensCalendarWithoutTextInput` |
| New/edit task | Last service date | `TaskFormScreenTest.newTaskDateOpensCalendarWithoutTextInput`, `editedTaskDateOpensCalendarWithoutTextInput` |
| New history record | Date | `HistoryDialogsTest.newRecordDateOpensCalendarWithoutTextInput` and `AppFlowsTest` |
| Edit history record | Date | `HistoryDialogsTest.recordCalendarPublishesTheChosenDate` |
| History filter | From date | `HistoryDialogsTest.filterStartDateOpensCalendarWithoutTextInput` |
| History filter | To date | `HistoryDialogsTest.filterEndDateHasItsOwnLabelAndCalendarCallback` |

The tests click the actual fields, verify absence of text-input semantics, choose a fixed leap day and verify the callback. The common picker test additionally verifies that the open calendar itself has no editable date input. Repository search found no other editable date field in common UI or platform hosts. Date displays and interval-month numbers are not date-entry fields. iOS runtime form acceptance remains separate from the passing compilation evidence.

## Separate commit verification

Each staged snapshot was built without the unfinished native transfer adapters in an isolated temporary checkout; the 26 tests of those uncommitted adapters are excluded from these counts.

Изолированный снимок коммита 1: Android 636/0/0/0; debug APK и обе iOS production/test compilation PASS. Workflow `3320ebaf092ce1a3434d4d5c9d210795`, вопрос «Собирается ли отдельный коммит 1?».

Изолированный снимок коммита 2: Android 649/0/0/0; debug APK и обе iOS production/test compilation PASS. Workflow `3320ebaf092ce1a3434d4d5c9d210795`, вопрос «Собирается ли отдельный коммит 2?».

Изолированный снимок коммита 3: Android 655/0/0/0; debug APK и обе iOS production/test compilation PASS. Workflow `3320ebaf092ce1a3434d4d5c9d210795`, вопрос «Собирается ли отдельный коммит 3?».

Изолированный снимок коммита 4: Android 658/0/0/0; debug APK и обе iOS production/test compilation PASS. Workflow `3320ebaf092ce1a3434d4d5c9d210795`, вопрос «Собирается ли отдельный коммит 4?».
