# Hotspot Scheduler

Native Android app (Kotlin, MVVM, Room, Coroutines/Flow, Hilt, Jetpack Compose) that schedules the
**Samsung Mobile Hotspot** and **mobile data** on/off via time-of-day routines, data-cap rules, or both.
Target: Android 13 (API 33, compileSdk 34), tested device profile: Samsung Galaxy A22, One UI 5.1.

Because Android 10 removed the public hotspot API, hotspot control requires UI automation. This app
drives the **Settings UI through an accessibility service**. There is no companion app, no ADB and
no root.

## What a scheduled toggle does

A scheduled routine is **one unattended job**. See "The transaction model" below — the lock happens
once, at the very end, after every requested change.

1. **Turn the screen on**, and verify `PowerManager.isInteractive` actually reports true.
2. **Unlock the phone** — platform dismissal (swipe lock or trusted state), then one bounded PIN-pad
   attempt, then wait for the person.
3. **Open the right Settings screen** and navigate to the switch.
4. **Read the switch state, click it, then read it again** to confirm. One retry on failure.
5. **Do the same for mobile data**, while still unlocked.
6. **Lock the phone once**, via `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`.

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

## The transaction model

**A scheduled routine is one unattended job, not a series of separate jobs.** This is the single
most important design decision in the app.

An earlier build called `lockScreen()` from a `finally` inside *every individual toggle*. A routine
needing both hotspot and mobile data therefore ran:

```
unlock -> hotspot ON -> LOCK -> mobile data ON   (on a now-locked phone)
```

so the second operation always failed. It also made the phone lock as soon as the first operation
finished, which looked like the app misbehaving on its own.

Now the lock belongs to the whole job:

```
transaction
  ├── ensureScreenAwake()        (verified, not assumed)
  ├── ensureUnlocked()           (platform -> PIN pad -> wait for the user)
  ├── hotspot change
  ├── mobile data change         (still unlocked)
  ├── collect per-step results
  └── lock ONCE at the very end
```

`executeBoundary()` and `runLiveTest()` both go through `transaction()`, which is the **only** place
in the app that locks the phone. The individual steps are private and never touch screen state.
The whole thing is serialised on one mutex, so a "turn off now" from the notification shade cannot
interleave with a boundary toggle.

Results are kept per step, so a partial failure is reported honestly rather than summarised as
success — `"hotspot changed; mobile data FAILED"` is a different outcome from success, and it is
logged that way.

Waking is a **verified** operation, not "a wake lock was acquired":

```
already interactive? -> done
else start the host activity (setTurnScreenOn) and poll PowerManager.isInteractive
else fall back to the deprecated wake lock and poll again
else abort with a distinct "screen did not wake" alert
```

Waking is independent of the keyguard. An earlier build refused to start the host activity at all
when a secure PIN was present, so a PIN-protected phone relied entirely on the deprecated wake lock
and the PIN-pad gestures were delivered to a dark screen.

The "already on screen" fast path is only taken when the display is interactive **and** the device
is unlocked **and** Settings is genuinely foreground. A locked phone can still hand back stale
cached Settings nodes, and acting on those means operating on a screen nobody can see.

## Acceptance behaviour

With a routine from 02:00 to 03:00 that wants hotspot and mobile data on:

| Time | What happens |
|---|---|
| 01:59 | screen off, locked, hotspot off, data off |
| 02:00 | alarm -> wake -> unlock -> hotspot ON -> data ON -> verify -> **lock once** |
| 02:01 | screen off, locked, hotspot **on**, data **on** |
| 03:00 | alarm -> wake -> unlock -> hotspot OFF -> data OFF -> verify -> **lock once** |
| 03:01 | screen off, locked, hotspot off, data off |

No interaction required. Setup has **Both ON** / **Both OFF** live-test buttons that exercise
exactly this path, so the behaviour can be checked without waiting for an alarm.

## Operation order matters

An internet-sharing hotspot needs an upstream. Turning the hotspot on before mobile data leaves a
network with no internet, and on a Galaxy A22 that frequently means the hotspot refuses to start at
all. So `OperationOrder` decides the sequence:

```
turning ON :  mobile data ON  -> verify -> hotspot ON  -> verify
turning OFF :  hotspot OFF     -> verify -> mobile data OFF -> verify
```

Only operations the user actually requested are included — a routine that wants just the hotspot
never touches mobile data. A unit test asserts that no unrequested feature ever appears in a plan.

## PIN keypad geometry is validated, never guessed

There is no `screenHeight * 0.8` fallback any more. Tap positions are derived from the keypad that was
actually detected, and `KeypadGeometryValidator` refuses to produce coordinates unless the layout
really looks like a numeric keypad:

- all ten digits `0..9` present
- exactly 3 columns and 4 rows
- `1` left of `2`, `1` above `4`, `0` below `8` (catches a mirrored or scrambled pad)
- no overlapping keys
- every key in the lower part of the screen, and of plausible button size

If validation fails the automation **skips the PIN** rather than guessing. That asymmetry is
deliberate: a skipped run costs nothing, a wrong digit costs a failed credential attempt and then a
five-minute backoff — which is exactly the "I pressed Diagnose and nothing happened" symptom.
## Failure discipline

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
- Alerts (cap reached, toggle failure, screen will not wake, phone needed unlocking) use a
  high-importance channel.

## These are the rules the code must never break

Each is covered by a unit test or asserted by a single call site:

1. **Never lock the phone in the middle of a job.** Exactly one call to `lockScreen()`, inside
   `lockAtEnd()`, called only from `transaction()`.
2. **Never act on a screen nobody can see.** A toggle fast path requires interactive **and**
   unlocked **and** Settings foreground.
3. **Never report success for an unverified change.** Every switch is read before and after the
   click; an unreadable state is a failure, never a pass.
4. **Never click a switch whose row text does not positively match.** A stale calibration is a
   hint, not an override: it goes through the same keyword/negative-word scoring as every other
   strategy, and there is no "first switch on the screen" fallback.
5. **Never loop on a credential.** One attempt per job, then a 5-minute backoff.
6. **Never use a secret as a password by accident.** Decryption failures return null rather than
   the stored ciphertext, because a Base64 blob is itself a valid 8–63 char passphrase.
7. **Never lose user data to a schema change.** No destructive database migration, and
   export/import round-trips the mobile-data flag and validates every field.

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
