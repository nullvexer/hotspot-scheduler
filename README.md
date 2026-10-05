# Hotspot Scheduler

Native Android app (Kotlin, MVVM, Room, Coroutines/Flow, Hilt, Jetpack Compose) that schedules the
**Samsung Mobile Hotspot** and **mobile data** on/off via time-of-day routines, data-cap rules, or both.
Target: Android 13 (API 33, compileSdk 34), tested device profile: Samsung Galaxy A22, One UI 5.1.

Because Android 10 removed the public hotspot API, hotspot control requires UI automation. This app
drives the **Settings UI through an accessibility service**. There is no companion app, no ADB and
no root.

## What a scheduled toggle does

1. **Turns the screen on** — a `SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP`.
2. **Dismisses the lock screen** where Android permits it (see the limitation below).
3. **Opens the right Settings screen** and navigates to the switch if it is not already up.
4. **Reads the switch state, clicks it, then reads it again** to confirm. One retry on failure.
5. **Turns the screen back off** via the device administrator, unless the user was already using
   the phone or the toggle failed while the screen is still needed.

Matching is safety-gated at every step: a switch is only clicked when its row text positively
matches ("Mobile Hotspot" / "Mobile data"), unrelated rows (Bluetooth, Data saver, Roaming) are
excluded by a negative score, only windows belonging to a Settings package are ever read, and a
saved calibration is treated as a hint rather than an override.

## The one hard limitation

**Android does not allow any app to enter a lock-screen PIN, pattern or password.** The keyguard is
not an accessibility window and does not accept injected input, and `KeyguardManager.disableKeyguard()`
is ignored while a secure lock is set. This is a platform restriction, not a missing feature.

So the app's behaviour depends on what you have set:

| Lock screen | Behaviour |
|---|---|
| No PIN, pattern or password | Fully hands-free. Wakes, dismisses the keyguard, toggles, sleeps. |
| Swipe-only | Fully hands-free. A non-secure keyguard can be dismissed programmatically. |
| PIN / pattern / password | Wakes the screen, notifies you, and waits up to 2 minutes for you to unlock. |

For hands-free nights with a PIN, either remove the lock credential (Settings → Lock screen), or use
**Settings → Lock screen → Extend Unlock → Trusted places**, or Smart Lock on a supported device —
in those cases the keyguard is not securely locked and the app dismisses it by itself. The Setup tab
shows this state explicitly with a red warning.

Turning the screen **off** requires device administrator, because `DevicePolicyManager.lockNow()` is
the only public API for it. The app requests exactly one policy capability: `force-lock`. No
password policies, no wipe, no camera disable.

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
