# HotspotScheduler vNext — Clean-Slate Architecture

**Target:** Samsung Galaxy A22, Android 13 (API 33), One UI 5.1  
**Principle:** One scheduled boundary = ONE automation transaction. Screen/keyguard lifecycle belongs to the transaction. Individual network operations NEVER own locking.

---

## 1. Module & Package Structure

Single Gradle module (`:app`), layered packages:

```
com.iranjan.hotspotscheduler
├── ui/                      # Compose screens, ViewModels, navigation, theme
│   ├── dashboard/
│   ├── routines/
│   ├── diagnostics/
│   ├── settings/
│   ├── setup/
│   ├── components/
│   └── theme/
├── domain/                  # Pure Kotlin, no Android deps
│   ├── model/               # Routine, DesiredNetworkState, ActualNetworkState, Feature, Target
│   ├── usecase/             # CreateRoutine, DeleteRoutine, ToggleNow, RunDiagnostics
│   ├── scheduler/           # DesiredStateResolver, RoutineEvaluator, BoundaryScheduler
│   └── automation/          # AutomationSession, TransactionStateMachine, Timeouts, FailureTaxonomy
├── data/                    # Persistence
│   ├── repository/          # RoutineRepository, ExecutionHistoryRepository
│   ├── datastore/           # AutomationPreferences (DataStore wrapper)
│   ├── encrypted/           # CredentialVault, PasswordCrypto (Keystore-backed)
│   └── database/            # Room: RoutineEntity, ExecutionRecordEntity, Daos, AppDatabase
├── platform/                # Android capability wrappers
│   ├── alarm/               # AlarmScheduler, ExactAlarmCapability
│   ├── screen/              # ScreenSession, WakeEngine, HostActivity
│   ├── keyguard/            # KeyguardEngine, PinPadResolver, CredentialVaultBridge
│   ├── accessibility/       # AccessibilityRuntime, AccessibilityService, NodeSelector
│   ├── network/             # NetworkStateReader (hotspot/data actual state)
│   ├── battery/             # BatteryOptimizationCapability
│   └── permissions/         # CapabilityProbe, PermissionCenter
└── automation/              # Orchestration layer
    ├── session/             # AutomationCoordinator, AutomationSession, SessionQueue
    ├── state/               # DesiredStateResolver (domain), ActualStateProbe
    ├── transaction/         # TransactionExecutor, StepExecutor, VerificationEngine
    ├── strategies/          # OperationStrategyResolver, HotspotStrategy, MobileDataStrategy
    ├── verification/        # StateVerification, SwitchVerification
    └── recovery/            # ReconciliationEngine, CrashRecovery
```

**Module rule:** No circular deps. `domain` ← `data/platform/automation` ← `ui`. `automation` imports `domain` + `platform`.

---

## 2. Core Domain Models (Pure Kotlin)

```kotlin
// domain/model/Feature.kt
enum class Feature { MOBILE_DATA, HOTSPOT }

// domain/model/Target.kt
sealed interface Target {
    data class Set(val on: Boolean) : Target
    object LeaveAlone : Target
}

// domain/model/DesiredNetworkState.kt
data class DesiredNetworkState(
    val hotspot: Target,
    val mobileData: Target
) {
    companion object {
        fun allOff() = DesiredNetworkState(Target.Set(false), Target.Set(false))
        fun untouched() = DesiredNetworkState(Target.LeaveAlone, Target.LeaveAlone)
    }
    fun targetFor(f: Feature) = when (f) {
        Feature.HOTSPOT -> hotspot
        Feature.MOBILE_DATA -> mobileData
    }
    val isFullyUntouched: Boolean get() = hotspot == Target.LeaveAlone && mobileData == Target.LeaveAlone
}

// domain/model/ActualNetworkState.kt
data class ActualNetworkState(val hotspot: Boolean?, val mobileData: Boolean?) {
    fun stateOf(f: Feature) = when (f) {
        Feature.HOTSPOT -> hotspot
        Feature.MOBILE_DATA -> mobileData
    }
    fun workRequired(desired: DesiredNetworkState): List<Feature> = Feature.entries.filter { f ->
        when (val t = desired.targetFor(f)) {
            is Target.LeaveAlone -> false
            is Target.Set -> stateOf(f) != t.on
        }
    }
    fun satisfies(desired: DesiredNetworkState) = workRequired(desired).isEmpty()
}

// domain/model/Routine.kt
data class Routine(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val daysOfWeek: Set<DayOfWeek>,  // 1=Mon .. 7=Sun
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hotspotTarget: Target,
    val mobileDataTarget: Target,
    val hotspotPassword: String?,  // encrypted at rest
    val priority: Int = 0
)

// domain/model/AutomationResult.kt
sealed class AutomationResult {
    data class Success(val steps: List<StepResult>, val finalLock: LockResult) : AutomationResult()
    data class PartialSuccess(val steps: List<StepResult>, val finalLock: LockResult, val failed: Feature) : AutomationResult()
    data class Failed(val reason: FailureReason, val partialSteps: List<StepResult> = emptyList()) : AutomationResult()
    data class Blocked(val reason: BlockReason) : AutomationResult()
}

enum class StepResult { ALREADY_OK, CHANGED, FAILED, BLOCKED }
enum class LockResult { LOCKED, ALREADY_LOCKED, FAILED, SKIPPED }
enum class FailureReason { ... }  // 20+ precise reasons per spec §59
enum class BlockReason { ... }
```

---

## 3. Desired-State Engine (Single Source of Truth)

```kotlin
// domain/scheduler/DesiredStateResolver.kt
class DesiredStateResolver(
    private val routineRepo: RoutineRepository,
    private val prefs: AutomationPreferences
) {
    data class Inputs(
        val now: Instant,
        val zone: ZoneId,
        val masterEnabled: Boolean,
        val paused: Boolean,
        val capReached: Boolean,
        val hotspotSuppressed: Boolean
    )

    fun resolve(inputs: Inputs): DesiredNetworkState {
        if (!inputs.masterEnabled || inputs.paused) return DesiredNetworkState.untouched()

        val active = routineRepo.enabledRoutines().filter { r ->
            RoutineEvaluator.isActive(r, inputs.now, inputs.zone)
        }
        if (active.isEmpty()) return DesiredNetworkState.allOff()

        val wantsData = active.any { it.mobileDataTarget == Target.Set(true) }
        val wantsHotspot = active.any { it.hotspotTarget == Target.Set(true) } || wantsData

        val dataTarget = if (wantsData) Target.Set(true) else Target.Set(false)
        val hotspotTarget = when {
            inputs.capReached || inputs.hotspotSuppressed -> Target.Set(false)
            wantsHotspot -> Target.Set(true)
            else -> Target.Set(false)
        }

        return DesiredNetworkState(hotspotTarget, dataTarget)
    }
}
```

**Invariant:** `DesiredStateResolver` is the ONLY place that computes desired state. The scheduler, transaction executor, and UI all read from it.

---

## 4. Automation Session State Machine (Explicit)

```kotlin
// domain/automation/TransactionStateMachine.kt
enum class SessionState {
    IDLE,
    PREPARING,
    WAKE_REQUESTED,
    SCREEN_AWAKE,
    KEYGUARD_CHECK,
    PLATFORM_DISMISS_ATTEMPT,
    PIN_REQUIRED,
    PIN_PAD_REVEAL,
    PIN_PAD_DETECTED,
    CREDENTIAL_ENTRY,
    UNLOCK_VERIFICATION,
    AUTOMATION_READY,
    NETWORK_PREPARATION,
    HOTSPOT_STEP,
    MOBILE_DATA_STEP,
    FINAL_STATE_VERIFICATION,
    RESTORING_UI,
    LOCK_REQUESTED,
    LOCK_VERIFICATION,
    SUCCESS,
    FAILED
}

data class SessionContext(
    val sessionId: String,
    val trigger: Trigger,
    val desiredState: DesiredNetworkState,
    val initialState: ActualNetworkState,
    var currentState: SessionState = SessionState.IDLE,
    var perStepResults: MutableMap<Feature, StepResult> = mutableMapOf(),
    var failureReason: FailureReason? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val timeoutPolicy: TimeoutPolicy
)

// Transition rules: each state has entryTime, timeout, expectedConditions, exitCondition, failureReason
// No hidden transitions. All logged via AutomationLogger.
```

---

## 5. Platform Abstractions

### 5.1 AccessibilityRuntime (Command Bus)

```kotlin
// platform/accessibility/AccessibilityRuntime.kt
interface AccessibilityRuntime {
    val state: StateFlow<RuntimeState>  // CONNECTED, DISCONNECTED, INTERRUPTED, UNAVAILABLE
    suspend fun getActiveWindows(): Result<List<WindowInfo>>
    suspend fun findNodes(selector: NodeSelector): Result<List<AccessibilityNodeInfo>>
    suspend fun click(node: AccessibilityNodeInfo): Result<Unit>
    suspend fun gesture(path: Path, duration: Long): Result<Unit>
    suspend fun globalAction(action: Int): Result<Unit>
    suspend fun waitForCondition(predicate: suspend () -> Boolean, timeout: Duration): Result<Boolean>
    suspend fun dumpCurrentUi(): Result<UiDump>
}

sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Failure(val error: AccessibilityError) : Result<Nothing>()
}

enum class AccessibilityError {
    ServiceUnavailable, NoActiveWindow, NodeNotFound, ActionRejected, Timeout, Interrupted
}
```

### 5.2 ScreenSession (Ownership)

```kotlin
// platform/screen/ScreenSession.kt
interface ScreenSession {
    suspend fun acquire(): Result<Unit>
    suspend fun ensureAwake(): Result<Unit>  // verifies isInteractive
    suspend fun ensureUnlocked(): Result<UnlockResult>
    suspend fun ensureSettingsForeground(target: SettingsTarget): Result<Unit>
    suspend fun release()
    suspend fun lock(): Result<LockResult>
}

enum class UnlockResult { ALREADY_UNLOCKED, PLATFORM_DISMISSED, CREDENTIAL_ENTERED, MANUAL_REQUIRED, FAILED }
```

### 5.3 KeyguardEngine (PIN Subsystem)

```kotlin
// platform/keyguard/KeyguardEngine.kt
interface KeyguardEngine {
    suspend fun detectKeyguard(): Result<KeyguardInfo>
    suspend fun revealPinPad(): Result<PinPadGeometry>
    suspend fun enterCredential(geometry: PinPadGeometry, pin: String): Result<CredentialResult>
    suspend fun verifyUnlocked(): Boolean
}

data class PinPadGeometry(
    val digitCentres: Map<Int, Pair<Float, Float>>,  // normalized 0..1
    val enterCentre: Pair<Float, Float>?,
    val sourcePackage: String,
    val validated: Boolean
)

enum class CredentialResult { UNLOCKED, KEYPAD_NOT_FOUND, DIGIT_FAILED, STILL_LOCKED, NOT_CONFIGURED, BACKOFF }
```

**Geometry validation mandatory before coordinate fallback** (spec §23).

---

## 6. Automation Orchestration

### 6.1 AutomationCoordinator (Single Serialized Queue)

```kotlin
// automation/session/AutomationCoordinator.kt
class AutomationCoordinator @Inject constructor(
    private val sessionQueue: SessionQueue,
    private val transactionExecutor: TransactionExecutor,
    private val alarmScheduler: AlarmScheduler,
    private val prefs: AutomationPreferences
) {
    private val mutex = Mutex()

    suspend fun executeBoundary(boundary: Boundary): AutomationResult = mutex.withLock {
        val session = AutomationSession.create(boundary)
        sessionQueue.enqueue(session)
        transactionExecutor.execute(session)
    }

    suspend fun executeManual(trigger: ManualTrigger): AutomationResult = mutex.withLock { ... }
    suspend fun executeCapEnforcement(): AutomationResult = mutex.withLock { ... }
}
```

### 6.2 TransactionExecutor (Owns the Transaction)

```kotlin
// automation/transaction/TransactionExecutor.kt
class TransactionExecutor @Inject constructor(
    private val screenSession: ScreenSession,
    private val keyguardEngine: KeyguardEngine,
    private val stepExecutor: StepExecutor,
    private val verificationEngine: VerificationEngine,
    private val logger: AutomationLogger
) {
    suspend fun execute(session: AutomationSession): AutomationResult {
        session.transitionTo(PREPARING)
        logger.logTransition(session, PREPARING)

        // 1. Wake
        session.transitionTo(WAKE_REQUESTED)
        screenSession.ensureAwake().onFailure { return AutomationResult.Failed(it.reason) }
        session.transitionTo(SCREEN_AWAKE)

        // 2. Unlock (one planned attempt)
        session.transitionTo(KEYGUARD_CHECK)
        val unlockResult = screenSession.ensureUnlocked()
        if (unlockResult.isFailure) return AutomationResult.Failed(unlockResult.reason)

        // 3. Network steps (ordered by OperationOrder)
        session.transitionTo(AUTOMATION_READY)
        val steps = OperationOrder.plan(session.desiredState)
        for (feature in steps) {
            session.transitionTo(stepState(feature))
            val result = stepExecutor.execute(feature, session.desiredState.targetFor(feature))
            session.perStepResults[feature] = result
            if (result == StepResult.FAILED || result == StepResult.BLOCKED) {
                return AutomationResult.PartialSuccess(...)
            }
        }

        // 4. Final verification
        session.transitionTo(FINAL_STATE_VERIFICATION)
        if (!verificationEngine.verifyAll(session.desiredState)) {
            return AutomationResult.Failed(VerificationFailed)
        }

        // 5. Lock exactly once
        session.transitionTo(LOCK_REQUESTED)
        val lockResult = screenSession.lock()
        session.transitionTo(LOCK_VERIFICATION)

        return AutomationResult.Success(session.perStepResults, lockResult)
    }
}
```

**Critical invariant enforced structurally:** No network step calls `lock()`. Only `TransactionExecutor` calls `screenSession.lock()` exactly once at finalization.

---

## 7. Strategy System (Capability-Driven)

```kotlin
// automation/strategies/OperationStrategyResolver.kt
interface NetworkOperationStrategy {
    suspend fun readState(): Result<Boolean>
    suspend fun setState(target: Boolean, password: String?): Result<StepResult>
    suspend fun verifyState(target: Boolean): Result<Boolean>
}

class OperationStrategyResolver @Inject constructor(
    private val hotspotStrategies: List<NetworkOperationStrategy>,
    private val dataStrategies: List<NetworkOperationStrategy>,
    private val accessibility: AccessibilityRuntime,
    private val screenSession: ScreenSession
) {
    fun resolveHotspot(): NetworkOperationStrategy = probeAndSelect(hotspotStrategies)
    fun resolveMobileData(): NetworkOperationStrategy = probeAndSelect(dataStrategies)

    private fun probeAndSelect(strategies: List<NetworkOperationStrategy>): NetworkOperationStrategy {
        // Try each in order: DirectApi → SettingsDeepLink → AccessibilitySettings → QuickSettings
        // Return first that reports capability
    }
}

// Strategies for Samsung A22 Android 13:
class SamsungSettingsHotspotStrategy(...) : NetworkOperationStrategy { ... }
class SamsungQuickSettingsDataStrategy(...) : NetworkOperationStrategy { ... }
class GenericAccessibilityFallbackStrategy(...) : NetworkOperationStrategy { ... }
```

---

## 8. Scheduler Integration

```kotlin
// domain/scheduler/BoundaryScheduler.kt
class BoundaryScheduler @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
    private val routineRepo: RoutineRepository,
    private val desiredStateResolver: DesiredStateResolver,
    private val prefs: AutomationPreferences
) {
    suspend fun rescheduleAll() {
        val nextBoundary = desiredStateResolver.nextBoundaryChange(routineRepo.enabledRoutines())
        alarmScheduler.scheduleExact(nextBoundary)
        alarmScheduler.scheduleMidnight()  // for cap reset, day rollover
    }
}
```

**Alarm triggers → AutomationCoordinator.executeBoundary()** — no business logic in receivers.

---

## 9. Persistence Layer

| Store | Purpose | Encryption |
|-------|---------|------------|
| Room (`AppDatabase`) | Routines, ExecutionRecord (history) | No (non-sensitive) |
| DataStore (`AutomationPreferences`) | Master toggle, pause, cap, calibration, last boundary | No |
| DE DataStore (`CredentialVault`) | Device unlock PIN | AES-GCM + AndroidKeyStore |
| DE DataStore (`PasswordVault`) | Hotspot passwords | AES-GCM + AndroidKeyStore (separate key) |

**Key separation:** Device PIN and hotspot passwords use **different Keystore aliases**.

---

## 10. Capability Probe (Setup-Time)

```kotlin
// platform/permissions/CapabilityProbe.kt
data class CapabilityReport(
    val screenWake: Capability,
    val accessibility: Capability,
    val keyguardDetection: Capability,
    val pinKeypad: Capability,
    val hotspotStrategy: Capability,
    val mobileDataStrategy: Capability,
    val exactAlarm: Capability,
    val backgroundExecution: Capability,
    val batteryOptimization: Capability,
    val overlay: Capability,
    val notifications: Capability
) {
    val overall: OverallStatus get() = when {
        this.any { it == Capability.BLOCKED } -> OverallStatus.BLOCKED
        this.any { it == Capability.DEGRADED } -> OverallStatus.DEGRADED
        else -> OverallStatus.READY
    }
}

enum class Capability { READY, DEGRADED, BLOCKED }
enum class OverallStatus { READY, DEGRADED, BLOCKED }
```

**Run on first setup + after every reboot.** Drives the "System Console" UI (§48).

---

## 11. Diagnostics Subsystem (First-Class)

```kotlin
// automation/diagnostics/AutomationDiagnostics.kt
class AutomationDiagnostics @Inject constructor(
    private val screenSession: ScreenSession,
    private val keyguardEngine: KeyguardEngine,
    private val strategyResolver: OperationStrategyResolver,
    private val accessibility: AccessibilityRuntime,
    private val logger: AutomationLogger
) {
    suspend fun runFullTest(dryRun: Boolean): DiagnosticReport
    suspend fun diagnoseWake(): WakeDiagnostic
    suspend fun diagnoseUnlock(): UnlockDiagnostic
    suspend fun diagnoseHotspot(): NetworkDiagnostic
    suspend fun diagnoseMobileData(): NetworkDiagnostic
    suspend fun diagnoseKeyguard(): KeyguardDiagnostic
    suspend fun dumpAccessibilityTree(): UiDump
}
```

**Output:** Human-readable instrument panel (§41) + structured export.

---

## 12. Test Architecture (4 Levels)

| Level | Scope | Tools | Examples |
|-------|-------|-------|----------|
| **1. Pure Unit** | Domain logic, no Android | JUnit, KotlinTest | `DesiredStateResolver`, `RoutineEvaluator`, `OperationOrder`, `TransactionStateMachine`, `KeypadGeometryValidator`, `TimeoutPolicy` |
| **2. Android Integration** | DataStore, Vault, AlarmManager, Permissions | Robolectric, `AndroidJUnitRunner` | `CredentialVault`, `AutomationPreferences`, `AlarmScheduler`, `CapabilityProbe` |
| **3. UI** | Compose screens, ViewModels | Compose Test, `createComposeRule` | Dashboard, RoutineEditor, SetupFlow, DiagnosticsPanel |
| **4. Physical Acceptance** | **Mandatory on Galaxy A22** | Manual + automated script | Spec §72 matrix (15 tests: A-O) |

**Critical Invariant Tests (§73):** Automated at Level 1/2:
1. No network step can lock → `TransactionExecutor` test with mock `ScreenSession` verifying `lock()` call count
2. ≤1 planned unlock → `KeyguardEngine` contract test
3. ≤1 final lock → `TransactionExecutor` test
4. No PIN retry loop → `KeyguardEngine` test with failed credential
5. Failed step ≠ success → `StepExecutor` test
6. Idempotent desired state → `DesiredStateResolver` + `ActualNetworkState` test
7. Old boundary ≤ active desired state → `DesiredStateResolver` overlap test
8. Stale nodes never reused → `AccessibilityRuntime` + `StepExecutor` test
9. Coordinate PIN impossible without validated geometry → `PinPadResolver` test
10. No network UI manipulation while `!isInteractive || isDeviceLocked` → `TransactionExecutor` state guard test

---

## 13. Timeout Policy (Centralized)

```kotlin
// domain/automation/TimeoutPolicy.kt
data class TimeoutPolicy(
    val screenWake: Duration = 8.seconds,
    val keypadReveal: Duration = 6.seconds,
    val keypadDetection: Duration = 3.seconds,
    val digitAction: Duration = 500.milliseconds,
    val unlockVerification: Duration = 3.seconds,
    val settingsLaunch: Duration = 10.seconds,
    val navigation: Duration = 5.seconds,
    val switchTransition: Duration = 3.seconds,
    val finalVerification: Duration = 5.seconds,
    val finalLock: Duration = 2.seconds,
    val totalTransaction: Duration = 90.seconds
) {
    companion object {
        val default = TimeoutPolicy()
        val samsungA22 = TimeoutPolicy(
            screenWake = 10.seconds,
            keypadReveal = 8.seconds,
            settingsLaunch = 12.seconds,
            navigation = 6.seconds
        )
    }
}
```

**No `delay()` in business logic.** Use `awaitCondition(predicate, timeout)` everywhere.

---

## 14. Security Model

| Secret | Storage | Key Alias | Export |
|--------|---------|-----------|--------|
| Device unlock PIN | DE DataStore | `device_unlock_key` | Never |
| Hotspot passwords | DE DataStore | `hotspot_pw_key` | Never |
| Execution history | Room | — | Exportable (sanitized) |

- Plaintext lifetime minimized: decrypt → use → zeroize (Kotlin `CharArray` for PIN)
- No credentials in logs, crash reports, Intents, notifications, screenshots
- `AttemptLog` / `AutomationLogger` redact automatically

---

## 15. Build & Static Quality

| Check | Tool | Config |
|-------|------|--------|
| Kotlin strictness | `kotlinOptions` | `-Xopt-in=kotlin.RequiresOptIn`, `-Werror` |
| Lint | Android Lint | `lintOptions { warningsAsErrors true }` |
| Detekt | `detekt` | `detekt.yml` with custom rules |
| Ktlint | `ktlint` | Standard Android style |
| Dependency check | `dependencyCheck` | OWASP, fail on CVSS ≥ 7 |
| Tests | `./gradlew testDebugUnitTest` | Required green |
| APK verify | `apksigner verify` | Required |

---

## 16. Implementation Phases (Per Spec §92)

| Phase | Deliverable | Verification |
|-------|-------------|--------------|
| **1** | Clean project: delete old `app/src/main/java`, create new package layout, `build.gradle.kts` | Compiles |
| **2** | Domain models (`Feature`, `Target`, `DesiredNetworkState`, `ActualNetworkState`, `Routine`, `AutomationResult`) | Level 1 tests pass |
| **3** | `DesiredStateResolver`, `RoutineEvaluator`, `BoundaryScheduler`, `OperationOrder` | Level 1 tests + invariant tests pass |
| **4** | `AccessibilityRuntime`, `AccessibilityService` (broad registration, cheap filter) | Level 2 tests |
| **5** | `ScreenSession`, `WakeEngine`, `HostActivity` | Level 2 tests + physical wake test |
| **6** | `KeyguardEngine`, `PinPadResolver`, `CredentialVaultBridge` | Level 2 tests + physical PIN test |
| **7** | `ScreenSession.lock()` via `GLOBAL_ACTION_LOCK_SCREEN` | Physical lock test |
| **8** | `SettingsAutomationEngine`, `NavigationStrategy`, `ScreenDetector`, `NodeSelector` | Level 2 tests |
| **9** | `SamsungSettingsHotspotStrategy`, `SamsungQuickSettingsHotspotStrategy` | Physical hotspot test |
| **10** | `SamsungSettingsDataStrategy`, `SamsungQuickSettingsDataStrategy` | Physical data test |
| **11** | `AutomationCoordinator`, `TransactionExecutor`, `SessionQueue` | Level 1/2 + invariant tests |
| **12** | `VerificationEngine`, `ReconciliationEngine`, `CrashRecovery` | Level 2 + crash test |
| **13** | `AutomationDiagnostics`, `CapabilityProbe`, `ExecutionHistoryRepository` | Level 2 + diagnostic UI |
| **14** | Modern UI (Dashboard, Routines, Diagnostics, Setup, Theme) | Level 3 tests |
| **15** | Physical hardening: run §72 matrix A-O on Galaxy A22 | **All PASS** |

**End of each phase:** Compile → Test → Inspect → Verify. Do not stack untested phases.

---

## 17. Physical Acceptance Matrix (§72) — Must All Pass

| Test | Precondition | Expected |
|------|--------------|----------|
| A | Unlocked, screen ON | Both ON, no intermediate lock, final lock |
| B | Unlocked, screen ON | Both OFF, final lock |
| C | Locked, screen OFF | Wake → unlock → data ON → hotspot ON → verify → **one final lock** |
| D | Locked, screen OFF | Wake → unlock → hotspot OFF → data OFF → verify → **one final lock** |
| E | Locked, screen OFF | Hotspot only, data unchanged, final lock |
| F | Locked, screen OFF | Data only, hotspot unchanged, final lock |
| G | Locked, screen OFF | Wake → reveal PIN pad → detect keypad → report IDs → **zero digits entered** |
| H | Locked, screen OFF | Wrong PIN → one attempt → no retry → failure recorded |
| I | During automation | Settings screen changes → selector fails safely → no blind click |
| J | During transaction | User manually locks → transaction detects → unsafe steps stop → failure reported |
| K | State already matches | No unnecessary toggles → verify → final lock |
| L | Two overlapping routines | `DesiredStateResolver` computes one deterministic state |
| M | Process killed | Next scheduled boundary still executes |
| N | Phone reboot | Schedules restored per security model, no ancient replay |
| O | Battery optimization ON | Setup reports DEGRADED/BLOCKED, user can fix |

**No test marked PASS without physical execution on Galaxy A22.**

---

## 18. Definition of Done

A release candidate is **only** complete when:

- [ ] All Level 1-3 tests pass
- [ ] Static analysis clean (lint, detekt, ktlint, dependency check)
- [ ] APK builds, installs, `apksigner verify` passes
- [ ] **Physical Samsung A22 test matrix A-O: ALL PASS**
- [ ] Automation invariants (8 tests) pass
- [ ] Diagnostics export from successful + failed runs archived
- [ ] Grep passes: no duplicate lock ownership, no blind PIN loops, no Device Admin, no hidden APIs, no per-toggle lock, no unbounded retries

---

**This architecture replaces the build-32 monolith with explicit, testable, verifiable layers. The transaction model is structurally enforced, not conventionally.**