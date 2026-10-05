# Hotspot Scheduler

Native Android app (Kotlin, MVVM, Room, Coroutines/Flow, Hilt, Jetpack Compose) that schedules the
**Samsung Mobile Hotspot** and **mobile data** on/off via time-of-day routines, data-cap rules, or both.
Target: Android 13 (API 33, compileSdk 34), tested device profile: Samsung Galaxy A22, One UI 5.1.

Because Android 10 removed the public hotspot API, hotspot control requires UI automation. This app
drives the **Settings UI through an accessibility service**. There is no companion app, no ADB and
no root.

## What a scheduled toggle does

1. **Turns the screen on** — a `SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP`, plus a
   transparent host activity with `setTurnScreenOn(true)` / `setShowWhenLocked(true)`.
2. **Unlocks the phone**, escalating — see below.
3. **Opens the right Settings screen** and navigates to the switch if it is not already up.
4. **Reads the switch state, clicks it, then reads it again** to confirm. One retry on failure.
5. **Locks the screen again** via `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`.

Matching is safety-gated at every step: a switch is only clicked when its row text positively
matches ("Mobile Hotspot" / "Mobile data"), unrelated rows (Bluetooth, Data saver, Roaming) are
excluded by a negative score, only windows belonging to a Settings package are ever read, and a
saved calibration is treated as a hint rather than an override.

## Unattended unlock

There is no Android API that accepts a credential — `KeyguardManager` cannot do it, and
`DevicePolicyManager.setKeyguardDisabled` is blocked on a device with a secure credential. But the
PIN pad is ordinary UI: AOSP's keyguard exposes real buttons (`key0`…`key9`, `key_enter`), and
clicking one appends the digit to the real credential field. That is the same action a person
performs, and it is what Tasker/AutoInput automate.

So the unlock engine escalates:

1. **Already unlocked** → nothing to do.
2. **Platform dismissal** — `KeyguardManager.requestDismissKeyguard()`. Its documented contract:
   a non-secure keyguard, *or a device in a trusted state*, is dismissed immediately with no user
   interaction. A trusted state is Smart Lock, Extend Unlock, or trusted places.
3. **The PIN pad** — `KeyguardAutomator` drives the lock screen keypad. Digits are located by
   **resource id**, never by visible text, because AOSP attaches `ObscureSpeechDelegate` to the
   keys so the spoken digit is suppressed. Ids tried per digit, in order:
   `com.android.keyguard:id/key{N}`, `com.android.systemui:id/key{N}`,
   `com.android.systemui:id/numpad_key{N}`, `com.android.samsung:id/key{N}` — the same key is
   `com.android.systemui:id/key1` on some Android 13 builds and `com.android.keyguard:id/key1` on
   others, so the resolver tries all four.
4. **Gesture fallback** — if a node exists but refuses `ACTION_CLICK`, a `dispatchGesture` tap is
   sent to its centre. If no node is found at all, a calibrated 4×3 grid position is used.
5. **Manual wait** — keeps the screen awake and finishes the moment the phone is unlocked.

### Failure discipline

This is the part that matters most. Android counts wrong credential attempts, and some devices
wipe on too many. So:

- **One attempt per scheduled change.** There is no `while (isLocked) enterPin()` loop anywhere.
- **A 5-minute backoff** after any failure, recorded in the vault. A flaky automation bug becomes
  a controlled failure instead of a lockout machine.
- **Every outcome is distinct** — `Unlocked`, `KeypadNotFound`, `DigitFailed`, `StillLocked`,
  `NotConfigured`, `Backoff` — and each is logged with its own reason.
- The keypad is only re-swiped if it is *not already visible*; a blind swipe can dismiss a bouncer
  that was about to accept input.
- Verification uses `KeyguardManager.isDeviceLocked()`, the authoritative "needs authentication"
  check, rather than `isKeyguardLocked()`, which also reports true for a swipe-only lock.

### Diagnosing a ROM change

Keypad ids change between One UI versions. Setup has **Diagnose keypad**, which wakes the screen and
reports which key ids are actually reachable, without entering anything. Run it on the lock screen
and read the log; the output tells you exactly which id form your phone uses, so `KeyguardIds` can
be extended instead of guessed.

### Where the PIN lives

The PIN is stored **encrypted with an Android Keystore AES-GCM key** (the same primitive as the
hotspot passwords), and it is **never logged and never exported** — the diagnostics log records only
the length, and export/import omits it.

It is written to **device-protected (DE) storage**, not normal app storage. This matters: normal app
storage is credential-encrypted and is unavailable until the phone has been unlocked once since boot,
so a 5am routine after a reboot could not otherwise read its own credential. DE storage is available
that early. The one remaining constraint is that a *reboot* still requires you to unlock once by
hand before the automation can act, which the Setup card states plainly.

Turning the screen **off** needs no extra permission: `GLOBAL_ACTION_LOCK_SCREEN` (API 28+) replaced
the Device Administrator that an earlier version required, and the `BIND_DEVICE_ADMIN` permission,
the admin receiver and the policy XML are all gone.

## Safety invariants

These are the rules the code must never break, each covered by a unit test:

1. **Never claim a hotspot is off when the state is unknown.** A switch whose state cannot be read
   is never reported as toggled.
2. **Never click a switch whose row text does not positively match.** A stale calibration goes
   through the same keyword/negative-word scoring as every other strategy, and there is no
   "first switch on the screen" fallback.
3. **Never use a secret as a password by accident.** Decryption failures return null rather than
   the stored ciphertext, because a Base64 blob is itself a valid 8–63 char passphrase.
4. **Never leave user data to chance.** No destructive database migration, and export/import
   round-trips the mobile-data flag and validates every field.

## Behavior decisions

- **Boundary-only enforcement**: ON at window start, OFF at window end; manual changes between
  boundaries are never fought.
- **Long-gap recovery**: if the app has not run for more than 6 hours, it does not replay a
  single stale boundary. It reads the actual hotspot state and converges on what the *current*
  schedule says, so a phone that was off for days can never be left with the hotspot stuck ON.
- **Strictest cap wins**: one shared daily hotspot counter (reset at midnight); overlapping
  routines use the lowest cap. Cap hit → OFF + suppressed until midnight. The cap is only
  enforced when Usage Access is granted — without it there is no data at all, so the app says
  so in the diagnostics log instead of pretending the limit is active.
- **Wall-clock windows**: a 01:00–04:00 routine ends at 04:00 local time on every day,
  including the two DST transitions each year. `start == end` is a zero-length window, not
  "always on".
- **"Turn off now"** suppresses automation until the next window starts; **"Pause for today"**
  pauses until midnight.
- **Optional mobile data per routine**: ON at window start; OFF at end only if no other active
  routine needs it.
- **Per-routine hotspot password** (optional): 8–63 printable ASCII, validated in the editor and
  by the engine; empty = keep the password already configured in Settings. Stored AES-GCM
  encrypted (Android Keystore, versioned ciphertext) and never exported. Passwords are never
  exported or logged.
- Alerts (cap reached, toggle failure, both engines unavailable) use a high-importance channel.

## Safety invariants

These are the rules the code must never break, each covered by a unit test:

1. **Never claim a hotspot is off when the state is unknown.** A failed `dumpsys` probe returns
   null, and null is not `false`. The data-cap path reports "turned off" to the user, so an
   unverified stop must not be reported as success.
2. **Never click a switch whose row text does not positively match.** A stale calibration is a
   hint, not an override: it goes through the same keyword/negative-word scoring as every other
   matching strategy, and there is no "first switch on the screen" fallback.
3. **Never use a secret as a password by accident.** Decryption failures return null rather than
   the stored ciphertext, because a Base64 blob is itself a valid 8–63 char passphrase.
4. **Never leave user data to chance.** No destructive database migration, and export/import
   round-trips the mobile-data flag and validates every field.

## Build

1. Open the folder in Android Studio (JDK 17). AGP 8.1.3 / Gradle 8.1.1 / Kotlin 1.9.10 / KSP.
2. Run on the Galaxy A22. Unit tests: `gradlew testDebugUnitTest` (also run in CI on every push;
   CI publishes every green build to GitHub Releases).

## Setup — permissions

Open the app's **Setup** tab; each card shows live status and opens the right system screen.

1. **Accessibility Service** — Settings → Accessibility → Installed apps → Hotspot Scheduler → On.
   This is the toggle engine and the only way the app can change the hotspot. Re-verified on
   every app open and every service tick.
2. **Screen lock** — shown with a red warning when a PIN/pattern/password is set, because Android
   then forbids automatic unlocking. Tap **Remove lock credential** for the settings screen, or use
   Extend Unlock / Smart Lock to keep a PIN and still allow unattended operation.
3. **Turn the screen off afterwards** — grant device administrator. One permission only
   (`force-lock`). Without it toggles still work but the screen stays on.
4. **Usage Access** — Settings → Apps → ⋮ → Special access → Usage access → Allow. Required for
   the data cap.
5. **Notifications** — Android 13 runtime dialog on first launch.
6. **Alarms and reminders** — Settings → Apps → Hotspot Scheduler → Alarms and reminders → Allow.
7. **Battery** — tap to ignore optimizations, then Battery and device care → Battery → Background
   usage limits → **Never sleeping apps** → add this app; make sure it is not in Deep sleeping apps.

## Calibration

Setup → Open calibration → Start → the hotspot Settings screen opens → tap the row that is the
real hotspot switch → confirm. Persisted and prioritised by the matcher, but still row-text gated.
Clear it after a One UI update.

## Diagnostics

Setup → Diagnostics: persistent log (survives restarts) of every automation attempt, including a
control-dump of any screen where a toggle failed, plus Share (text) and Clear buttons. The
**Live test** card fires Hotspot ON/OFF and Data ON/OFF on demand using the same code path as
routines.

## Architecture

```
ui        Compose screens + ViewModels (routines, editor, setup, calibration, usage)
service   HotspotAutomationService (foreground), NotificationHelper, WidgetProvider
accessibility  HotspotAccessibilityService, NodeMatcher, NodeDumper, ScreenControl,
               ScreenOffAdminReceiver, controller facade
core      RoutineEvaluator (pure, unit-tested), AlarmScheduler, receivers
data      Room, DataStore, UsageMonitor (NetworkStatsManager), repository
di/util   Hilt modules, PasswordCrypto (Keystore), PassphraseRules, AttemptLog
```
