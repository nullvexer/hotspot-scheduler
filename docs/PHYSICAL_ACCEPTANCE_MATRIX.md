# Physical Acceptance Matrix — Galaxy A22 / Android 13 / One UI 5.1

**Device:** Samsung Galaxy A22 (SM-A225F)  
**Android:** 13 (API 33)  
**One UI:** 5.1  
**App Version:** vNext (clean-slate rebuild)

---

## Pre-Test Setup

Before running any tests, complete the Setup tab in order:

1. **Accessibility** → Settings → Accessibility → Installed apps → Hotspot Scheduler → ON
2. **Secure PIN** → If PIN/pattern/password set, either:
   - Remove lock credential, OR
   - Enable Extend Unlock / Smart Lock (trusted place/device)
3. **Exact Alarms** → Settings → Apps → Hotspot Scheduler → Alarms and reminders → Allow
4. **Battery** → Settings → Battery → Background usage limits → Never sleeping apps → Add Hotspot Scheduler
5. **Notifications** → Allow on first launch
6. **PIN** → Setup tab → Enter 4-16 digit PIN → Save → Enable auto-unlock

**Capability Probe must show:** `OVERALL: READY` (all 12 capabilities READY)

---

## Test Matrix (Spec §72)

| Test | Precondition | Action | Expected Result | Pass/Fail |
|------|--------------|--------|-----------------|-----------|
| **A** | Phone unlocked, screen ON | Run "Both ON" test | Mobile data ON → Hotspot ON → no intermediate lock → final lock | ☐ |
| **B** | Phone unlocked, screen ON | Run "Both OFF" test | Hotspot OFF → Mobile data OFF → final lock | ☐ |
| **C** | Phone locked, screen OFF | Schedule 02:00→03:00 Both ON, wait for 02:00 | Wake → unlock → data ON → hotspot ON → verify → **one final lock** | ☐ |
| **D** | Phone locked, screen OFF | Schedule 02:00→03:00 Both ON, wait for 03:00 | Wake → unlock → hotspot OFF → data OFF → verify → **one final lock** | ☐ |
| **E** | Phone locked, screen OFF | Hotspot only routine | Wake → unlock → hotspot changes → data unchanged → final lock | ☐ |
| **F** | Phone locked, screen OFF | Data only routine | Wake → unlock → data changes → hotspot unchanged → final lock | ☐ |
| **G** | Phone locked, screen OFF | Run "Diagnose Keyguard" | Wake → reveal PIN pad → detect keypad → report IDs → **zero digits entered** | ☐ |
| **H** | Phone locked, screen OFF | Configure wrong PIN, run test | One attempt → no retry → failure recorded → phone protected | ☐ |
| **I** | During automation | Change Settings screen manually | Selector fails safely → no blind click → diagnostic reports mismatch | ☐ |
| **J** | During transaction | User manually locks phone | Transaction detects → unsafe steps stop → failure reported → no repeated PIN | ☐ |
| **K** | State already matches desired | Run automation | No unnecessary toggles → verify → final lock | ☐ |
| **L** | Two overlapping routines | Routine A: 02:00-03:00 Both ON, Routine B: 02:30-04:00 Both ON | DesiredStateResolver computes single deterministic state → no boundary fights | ☐ |
| **M** | Process killed | Kill app between boundaries | Next scheduled boundary still executes | ☐ |
| **N** | Phone reboot | Reboot device | Schedules restored per security model → no ancient boundary replay | ☐ |
| **O** | Battery optimization ON | Enable battery optimization for app | Setup clearly reports DEGRADED/BLOCKED → user can fix → no false READY | ☐ |

---

## Critical Invariant Checks (Spec §73)

| Invariant | Test Method | Expected |
|-----------|-------------|----------|
| **1. No network step can lock** | Mock ScreenSession, verify `lock()` call count = 0 during steps, 1 at end | `lock()` called exactly once |
| **2. ≤1 planned unlock** | Mock KeyguardEngine, verify `enterCredential` call count ≤ 1 | ≤ 1 credential attempt |
| **3. ≤1 final lock** | Mock ScreenSession, verify `lock()` call count = 1 at finalization | Exactly 1 lock call |
| **4. No PIN retry loop** | Mock failed credential, verify no retry | Single attempt, then BACKOFF |
| **5. Failed step ≠ success** | Mock strategy returning FAILED, verify result | Result = Failed/PartialSuccess |
| **6. Idempotent desired state** | Actual = Desired, run automation | No toggles, verify only |
| **7. Old boundary ≤ active desired** | Overlapping routines, verify DesiredStateResolver output | Current active routines win |
| **8. Stale nodes never reused** | Navigate Settings, verify nodes refreshed | Fresh nodes after each transition |
| **9. Coordinate PIN impossible without geometry** | Mock invalid geometry, verify PIN not entered | Coordinate fallback blocked |
| **10. No network UI while locked** | Mock locked state, verify transaction aborts | Transaction blocked at KEYGUARD_CHECK |

---

## Automation Invariant Verification Commands

Run these grep searches to verify no regressions:

```bash
# No duplicate lock ownership
grep -r "lockScreen\|lockPhone\|GLOBAL_ACTION_LOCK" --include="*.kt" | grep -v "TransactionExecutor\|ScreenSession"

# No blind PIN loops
grep -r "while.*locked\|retry.*pin\|enterPin" --include="*.kt"

# No abandoned Device Admin code
grep -r "DeviceAdmin\|force-lock\|DevicePolicyManager" --include="*.kt"

# No hidden APIs/reflection
grep -r "Class\.forName\|Method\.invoke\|Field\.setAccessible" --include="*.kt"

# No per-toggle lock calls
grep -r "\.lock\(\)" --include="*.kt" | grep -v "TransactionExecutor\|ScreenSession"

# No unbounded retry loops
grep -r "while(true)\|for(;;)\|repeat.*Int.MAX" --include="*.kt"
```

All searches must return **zero matches** (except in TransactionExecutor, ScreenSession, KeyguardEngine where expected).

---

## Build Verification Checklist

| Check | Status |
|-------|--------|
| Unit tests pass (`./gradlew testDebugUnitTest`) | ☐ |
| Instrumentation tests pass | ☐ |
| Lint clean | ☐ |
| Detekt clean | ☐ |
| Ktlint clean | ☐ |
| Dependency check (no CVSS ≥ 7) | ☐ |
| APK builds (`./gradlew assembleDebug`) | ☐ |
| APK installs on Galaxy A22 | ☐ |
| `apksigner verify` passes | ☐ |
| Automation invariants (10 tests) pass | ☐ |
| Physical device test matrix A-O: ALL PASS | ☐ |
| Failure diagnostics exported from successful + failed runs | ☐ |

---

## Release Criteria

**DO NOT RELEASE until ALL of the above are checked.**

If any physical test is not executed, mark it `UNVERIFIED` — never call it `PASS`.

---

## Release Tag Format

```
build-<github_run_number>
```

Release notes auto-generated by CI (see `.github/workflows/build.yml`).