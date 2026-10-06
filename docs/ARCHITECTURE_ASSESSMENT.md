# Internal Architecture Assessment: HotspotScheduler (Build-31/32)

**Date:** 2026-10-06  
**Repository:** https://github.com/erfanbee/hotspot-scheduler.git  
**Branch:** main (31 commits)  
**Target Device:** Samsung Galaxy A22, Android 13 (API 33), One UI 5.1

---

## 1. What Exists (Build-32 Current State)

### Architecture
- **Single-module** Android app (`:app`), package `com.iranjan.hotspotscheduler`
- **Stack:** Kotlin 1.9.10, AGP 8.1.3, Gradle 8.1.1, KSP
- **UI:** Jetpack Compose + Material 3, Navigation-Compose, Hilt DI
- **Persistence:** Room (SQLite) for routines/usage, DataStore (Preferences) for settings
- **Automation:** AccessibilityService-driven UI automation (no root, no ADB, no Shizuku)
- **CI:** GitHub Actions → unit tests → debug APK → publish to GitHub Releases

### Key Components (31 source files scanned)

| Layer | Files | Responsibility |
|-------|-------|----------------|
| **Service** | `HotspotAutomationService.kt` | Foreground service, 2-min tick loop, boundary evaluation, alarm reschedule |
| **Accessibility** | `HotspotAccessibilityService.kt` | Service entry, calibration dump, keyguard automator host |
| **Controller** | `HotspotController.kt` (600 lines) | **Transaction engine** - wake → unlock → toggles → lock once |
| **Keyguard** | `KeyguardAutomator.kt` (364 lines) | PIN-pad driver via accessibility, geometry validation, bounded attempts |
| **Screen** | `ScreenControl.kt` (257 lines) | Wake (host activity + wake-lock fallback), dismiss, unlock orchestration, lock |
| **Navigator** | `HotspotNavigator.kt` | Resolves Settings target Activities for hotspot/data screens |
| **Node Matcher** | `NodeMatcher.kt` (354 lines) | Finds toggle switches by calibration/ID/class/text, reads state, clicks |
| **Scheduler** | `AlarmScheduler.kt` (151 lines) | Exact alarms via `setAlarmClock`, midnight + next boundary, cancellation |
| **Evaluator** | `RoutineEvaluator.kt` (168 lines) | Pure time-window logic, DST-safe, active routines, strictest cap |
| **Order** | `OperationOrder.kt` (66 lines) | Data→Hotspot ON; Hotspot→Data OFF; only requested ops |
| **Desired State** | `DesiredStateResolver.kt` (80 lines) | **New, uncommitted** - computes desired network state from all routines |
| **Network State** | `NetworkState.kt` (86 lines) | **New, uncommitted** - Feature/Target/DesiredNetworkState/ActualNetworkState |
| **Crypto** | `PasswordCrypto.kt` (104 lines) | AES-GCM + AndroidKeyStore for hotspot passwords |
| **Vault** | `LockCredentialVault.kt` (145 lines) | DE storage + AES-GCM for device unlock PIN |
| **Prefs** | `AutomationPrefs.kt` (114 lines) | DataStore keys: master, pause, cap, calibration, last boundary, AP config |
| **Models** | `Routine.kt`, `Entities.kt`, `CalibrationSignature.kt` | Domain ↔ Entity mapping with crypto |
| **DI** | `AppModule.kt` | Hilt: Database, DataStore, Controller binding |
| **UI** | `AppNav.kt`, 6 screens, ViewModels | Compose navigation: Routines, Usage, Setup, Editor, Calibration |

### Git State
- **Committed:** 31 commits, latest `593ba17` ("Fix operation order and remove blind PIN coordinates")
- **Uncommitted (3 paths):** `domain/automation/DesiredStateResolver.kt`, `domain/automation/NetworkState.kt`, `ui/components/`, `ui/theme/DesignSystem.kt` — new desired-state engine not yet wired into scheduler tick
- **Two APKs:** `app/build/outputs/apk/debug/app-debug.apk` (53.7 MB, 10/5) and folder-root `app-debug.apk` (52.6 MB, 9/11)

---

## 2. User-Facing Capabilities That Must Be Preserved

| Capability | Description |
|------------|-------------|
| **Time-window routines** | Start/end times, days-of-week, hotspot ON/OFF, mobile data ON/OFF |
| **Per-routine hotspot password** | Optional 8-63 ASCII, AES-GCM encrypted, applied on enable |
| **Daily data cap** | Shared counter (midnight reset), strictest cap wins across overlapping routines |
| **Boundary-only enforcement** | ON at window start, OFF at window end; manual changes between boundaries not fought |
| **Long-gap recovery** | >6h gap → converge to current schedule, not replay stale boundary |
| **Manual overrides** | "Turn off now" (suppress until next window), "Pause for today" (until midnight) |
| **Unattended unlock** | PIN stored encrypted in DE storage, driven via accessibility keypad |
| **Calibration** | User taps hotspot row once → persisted as hint for matcher |
| **Diagnostics** | Persistent log + screen dumps + live test buttons (Both ON/OFF) |
| **Widget + Notifications** | Status tile with pause/off actions, alerts for failures/cap/unlock |
| **Setup flow** | Permission cards with live status + deep links to system settings |

---

## 3. Automation Code to Replace (Per Spec: "Do NOT incrementally patch")

| Component | Why Replace |
|-----------|-------------|
| `HotspotController.kt` (600 lines) | Monolithic transaction + toggle logic mixed; per-toggle lock ownership historically; scattered timeouts; Samsung-specific navigation hardcoded |
| `KeyguardAutomator.kt` | Geometry validation good, but fused with accessibility calls; no `AccessibilityRuntime` abstraction; coordinate fallback logic still present |
| `ScreenControl.kt` | Wake/unlock/lock scattered; host activity logic mixed with power-manager fallback; no `ScreenSession` ownership model |
| `NodeMatcher.kt` | Single giant function with 6 strategies; no `ScreenDetector`/`NavigationStrategy` separation; Samsung text labels hardcoded |
| `HotspotNavigator.kt` | Hardcoded component names; no capability probing or strategy selection |
| `HotspotAutomationService.kt` | Tick loop owns scheduling + execution + notification + widget; no `AutomationCoordinator` / `AutomationSession` |
| `AlarmScheduler.kt` | Works but coupled to `RoutineEvaluator.Boundary`; no abstraction for capability-gated scheduling |
| `RoutineEvaluator.kt` | Pure and well-tested — **keep the logic**, but integrate with new `DesiredStateResolver` |
| `OperationOrder.kt` | Pure and correct — **keep** |
| `DesiredStateResolver.kt` / `NetworkState.kt` (uncommitted) | Correct direction but not integrated; needs to be the single source of truth for desired state |

---

## 4. Old UI Code to Replace

| Area | Issue |
|------|-------|
| All Compose screens (`RoutinesScreen`, `SetupScreen`, `RoutineEditorScreen`, `CalibrationScreen`, `UsageScreen`, `MainActivity`) | Generic Material 3 dashboard; not the "abstract automation control plane" specified |
| `AppNav.kt` | Standard bottom-bar nav; not the temporal visualization specified |
| Theme (`Theme.kt`, `DesignSystem.kt`) | Standard M3 theming; not the custom visual system specified |

---

## 5. Code Depending on Build-31/32 Assumptions

| Assumption | Where It Lives | Risk |
|------------|----------------|------|
| Per-toggle screen lifecycle | `HotspotController.runToggle()` launches/navigates per operation | Violates "screen belongs to transaction" invariant |
| Wake = wake-lock acquired | `ScreenControl.ensureScreenAwake()` fallback path | Doesn't verify `isInteractive` on fallback |
| Secure keyguard → skip host activity | Old code (now fixed but logic remains) | Host activity works regardless of keyguard |
| Stale Settings nodes usable | `runToggle()` Stage 0 fast-path checks `settingsForeground()` but nodes may be cached | Fixed by gating on `isInteractive && !isDeviceLocked` but fragile |
| Blind coordinate fallback | `KeyguardAutomator.tapDigit()` geometry validation → coordinate fallback | Spec forbids coordinate fallback without validated geometry |
| Shizuku references in CI/docs | `.github/workflows/build.yml:55`, README Setup step 3 | Stale documentation |
| Device Admin / `force-lock` in README | README lines 249-250 | Removed in code (`GLOBAL_ACTION_LOCK_SCREEN` used) but docs not updated |
| `DesiredStateResolver.wantsHotspot = ... \|\| true` | `DesiredStateResolver.kt:55` | Dead code - `|| true` makes preceding conditions unreachable |
| Single `HotspotController` mutex | `toggleLock` serializes everything | Correct for now, but no `AutomationSession` cancellation support |

---

## 6. Lessons Learned (from git history)

| Commit | Lesson |
|--------|--------|
| `1994c5f` | **Transaction model is critical** - per-toggle locking broke multi-op routines |
| `8f83822` | **PIN pad via accessibility works** but requires resource-id resolution, not text |
| `593ba17` | **Blind coordinates are fatal** - geometry validation prevents wrong-digit entry |
| `0a66223` | **Shizuku removed** - accessibility-only is the correct path for non-root |
| `669f290` | **False success was rampant** - every toggle now reads before/after, verifies |
| `0758137` | **dumpsys parsing is brittle** - don't rely on shell output format |
| `7063bdd` | **Shell deadlocks are real** - accessibility is safer than `cmd`/`su` |
| `9414c91` | **Rebind logic needed** - accessibility service can be interrupted |

---

## 7. Gaps vs. Spec Requirements

| Spec Section | Current State | Gap |
|--------------|---------------|-----|
| **7. State Machine** | Implicit in `transaction()` + tick loop | No explicit `AutomationSession` states, no structured logging per transition |
| **8. No Per-Toggle Locking** | Fixed in `HotspotController` but not structurally enforced | Architecture allows regression |
| **11. Capability-Driven Strategy** | Single hardcoded Settings path | No `OperationStrategyResolver`, no Quick Settings strategy |
| **12-13. Settings Automation Engine** | `HotspotController` + `HotspotNavigator` + `NodeMatcher` fused | No separation of navigation/detection/execution/verification |
| **14. Device Profiles** | Samsung package names hardcoded | No profile system, no runtime capability detection |
| **15. Discovery Before Action** | `NodeDumper` exists but not integrated into flow | No pre-action UI fingerprinting |
| **17. Accessibility Command Bus** | Direct `service.rootInActiveWindow` / `dispatchGesture` calls scattered | No `AccessibilityRuntime` |
| **18. Never Act on Stale Nodes** | Partial fix (Stage 0 check) | Not systematic - nodes reused after navigation |
| **19-25. PIN Unlock Engine** | `KeyguardAutomator` is close but fused | Needs separation: `KeyguardEngine` + `PinPadResolver` + `CredentialVault` |
| **26-30. Screen Session / Wake / Lock** | `ScreenControl` + `ToggleHostActivity` | No `ScreenSession` ownership, no `ensureSettingsForeground()` |
| **32. Network Verification** | Done in `attempt()` but per-toggle | Needs `VerificationEngine` at transaction level |
| **38. UI Fingerprinting** | Calibration signature only | No screen-type fingerprints for hotspot/data/keyguard |
| **39. Calibration System** | Single hotspot-row calibration | No keypad geometry persistence, no device profile versioning |
| **40-41. Diagnostics Subsystem** | `AttemptLog` + screen dump + live tests | Not a first-class instrument panel; no structured export |
| **42. Full Test Button** | "Both ON/OFF" live tests exist | No single "RUN FULL AUTOMATION TEST" with dry-run mode |
| **43-46. Scheduler / Reboot / Time** | `AlarmScheduler` + `SystemEventsReceiver` | Works but not integrated with `DesiredStateResolver` |
| **47. Battery/Samsung Reliability** | Setup cards mention it | No automated capability probe with READY/DEGRADED/BLOCKED |
| **48. Capability Center** | Setup tab with cards | Not the "sophisticated system console" specified |
| **51-52. Overlapping Routines / Desired State** | `DesiredStateResolver` exists uncommitted | Not wired; `RoutineEvaluator.activeRoutines` used directly in tick |
| **55. Concurrency** | `Mutex` in service + controller | No cancellation, no safe boundaries |
| **57. Timeouts** | Scattered constants (`STATE_TIMEOUT_MS`, etc.) | No centralized `TimeoutPolicy` |
| **58-59. Logging / Failure Taxonomy** | `AttemptLog` + `ToggleResult` | Not structured `AutomationResult` with precise failure types |
| **60-69. Modern UI** | Standard Compose dashboard | Complete replacement required |
| **71-73. Test Architecture** | 7 unit tests (pure) | Missing: integration, UI, physical acceptance matrix |
| **74. No Sleep-Based Engine** | Many `delay()` calls remain | Need `awaitCondition` primitives |
| **75. Capability Probe** | Manual setup cards only | No automated probe on first setup |
| **76. Device Self-Diagnosis** | Calibration captures some data | Not automatic on first run |
| **77. Security Model** | Keystore + DE storage correct | Good - preserve |
| **78. Crash Recovery** | Tick loop catches all, logs, continues | No structured reconciliation after crash |
| **79. Emergency Stop** | "Pause for today" / "Turn off now" | No `STOP AUTOMATION` with safe cancellation |
| **80. "READY" Meaning** | Status notification shows accessibility state | Dashboard doesn't show aggregate readiness |

---

## 8. Verdict

**The build-32 codebase is a working reference implementation with significant architectural debt.**  
It correctly implements the **transaction model** (single wake/unlock/toggle/lock), **PIN geometry validation**, **operation ordering**, and **idempotent boundary evaluation**. These are the *lessons learned* to carry forward.

**What must be discarded:** the monolithic `HotspotController`, fused screen/keyguard/navigation logic, hardcoded Samsung paths, scattered timeouts, stale documentation, and generic UI.

**What must be built fresh:** a layered architecture with explicit `AutomationSession` state machine, `AccessibilityRuntime` command bus, `ScreenSession` ownership, `DesiredStateResolver` as single source of truth, capability-driven strategy system, structured `AutomationResult`, and the specified modern UI.

**Risk:** The uncommitted `DesiredStateResolver`/`NetworkState` is the right direction but untested and unwired. The physical device acceptance matrix (spec §72) has never been fully executed against the current code.

---

**Next Step:** Write the new clean-slate architecture document (`ARCHITECTURE.md`) per spec §0-5, then begin Phase 1 implementation.