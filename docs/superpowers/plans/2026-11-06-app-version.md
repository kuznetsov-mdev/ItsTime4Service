# App version display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Show the installed app version and build number below licenses, using version `1.0.0` and build `2`.

**Architecture:** Shared settings content receives an `AppVersion` value. A small Compose expect/actual leaf reads Android package or iOS bundle metadata and remembers the result. No business-layer or dependency changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android package metadata, Foundation NSBundle.

**Spec:** `specs/presentation.md`, settings section; `docs/index.html`, settings mockup. User approved the version and build display in this session.

## Global Constraints

- Work in the current branch; no automatic commits or file deletion.
- Display «Версия 1.0.0 (2)» under licenses, non-interactive, with RU/EN/ES resources.
- Read installed metadata; use fakes for plain UI tests and previews.
- Verify Android host tests and compile iOS production and test sources through `gradle-run`.

## Review Focus

- Empty car list must still expose the app version.
- A different supplied version/build must appear without hardcoded release text.
- Loading and error states must retain their existing behavior.
- Native metadata may be absent in a preview/test host; do not crash the UI.
- Long version/build text must wrap rather than be clipped or made interactive.

### Task 1: Installed app version in settings

**Files:** `docs/index.html`, `specs/presentation.md`, `specs/test-plan.md`, `dev_plan.md`, `androidApp/build.gradle.kts`, `iosApp/Configuration/Config.xcconfig`; create `ui/AppVersion.kt`, `ui/AppVersion.android.kt`, `ui/AppVersion.ios.kt` in the matching shared source sets; update `features/settings/SettingsScreen.kt`, `SettingsScreenTest.kt`, and all three `strings.xml`.

**Interfaces:** `internal data class AppVersion(val name: String, val buildNumber: String)`; `@Composable internal expect fun rememberAppVersion(): AppVersion`; `SettingsContent` receives `appVersion: AppVersion`.

- [x] Update mockup and specification; record the user's approval.
- [x] Add a UI test supplying `2.3.4` / `17`, asserting localized version text after scrolling and no click action. Include absence in loading/error states.
- [x] Implement native metadata leaves and shared text; update Android/iOS version settings and previews.
- [x] Run Android host tests and iOS production/test compilation; build a signed release AAB.
- [x] Verify AAB version/signature, update project documentation, and report the replacement upload step. Offer a commit only after user approval; do not create it automatically.

## Verification results

06.11.2026: Android host tests passed (497 tests, including 16 settings tests); iOS production and test sources compiled; signed release AAB built. Version `1.0.0`, build `2`, confirmed in merged release manifest; all 137 non-META-INF bundle entries verified against the upload certificate. RU/EN/ES resource placeholders, mockup JavaScript syntax and `git diff --check` validated. No screenshot or manual device check of the new version text was performed. No commit created.
