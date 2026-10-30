# Reminder Window Default Implementation Plan

**Goal:** Use the saved settings value to prefill the reminder window on newly created maintenance tasks.

**Architecture:** Read the existing settings value in both task creation flows and convert kilometers into the unit shown by the form. Existing tasks keep their stored value, and presets continue to provide their own reminder windows.

**Tech Stack:** Kotlin Multiplatform, Compose, coroutine ViewModels, common tests.

**Spec:** `specs/domain.md` — `Settings.defaultReminderWindowKm` and task reminder window.

## Global Constraints

- Store reminder distances in kilometers and convert only for display/input.
- Do not change existing task values when editing.
- Preset values take precedence over the settings default.
- Keep comments in English.

## Review Focus

- Changing the distance unit must display the same physical distance.
- Editing an existing task must retain its saved reminder window.
- Preset tasks must retain their preset-specific reminder window.
- Blank optional reminder input must not silently override the visible default.

---

### Task 1: Prefill the task creation forms

**Files:**
- Modify: `shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/features/taskform/TaskFormViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/features/carform/CarFormViewModel.kt`
- Test: `shared/src/commonTest/kotlin/ru/kuznetsov/itstime4service/features/taskform/TaskFormViewModelTest.kt`
- Test: `shared/src/commonTest/kotlin/ru/kuznetsov/itstime4service/features/carform/CarFormViewModelTest.kt`

- [x] Add tests proving a new task and a new car's blank task block receive the configured default in the selected unit.
- [x] Add/adjust tests proving editing and preset flows preserve their own values.
- [x] Pass `defaultReminderWindowKm` from observed settings into initial new-task fields and initial/added blank car task blocks; retain current conversion to/from kilometers.
- [x] Run the focused Android host tests for the two ViewModel test classes.

### Task 2: Keep the form's saved value aligned

**Files:**
- Modify: `shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/features/taskform/TaskFormViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/features/carform/CarFormViewModel.kt`

- [x] Ensure the prefilled value is serialized as an explicit task reminder window, so normalization's 15% fallback is not used for a newly created task with the default shown.
- [x] Verify changed files and focused test results; update `dev_plan.md` if the discrepancy is resolved.
