package com.iranjan.hotspotscheduler.service

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import android.content.pm.ServiceInfo
import com.iranjan.hotspotscheduler.accessibility.HotspotController
import com.iranjan.hotspotscheduler.accessibility.ToggleResult
import com.iranjan.hotspotscheduler.core.AlarmScheduler
import com.iranjan.hotspotscheduler.core.RoutineEvaluator
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.data.usage.UsageMonitor
import com.iranjan.hotspotscheduler.data.usage.UsageSample
import com.iranjan.hotspotscheduler.util.AccessibilityUtils
import com.iranjan.hotspotscheduler.util.Formatters
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class HotspotAutomationService : LifecycleService() {

    @Inject lateinit var repo: RoutineRepository
    @Inject lateinit var prefs: AutomationPrefs
    @Inject lateinit var usageMonitor: UsageMonitor
    @Inject lateinit var controller: HotspotController
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Serialises tick() against the notification actions. Without it a "turn off now" from the
     * shade could interleave with a boundary toggle and leave the hotspot in the wrong state.
     */
    private val workLock = Mutex()

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.ID_STATUS,
            notifications.buildStatusNotification(initialStatus()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        lifecycleScope.launch {
            // lifecycleScope runs on Dispatchers.Main.immediate, but a tick performs up to
            // 4x1500 accessibility-node traversals plus blocking shell calls. Running that on
            // the main looper is an ANR. Nothing in the tick touches the UI directly.
            withContext(Dispatchers.Default) {
                // Alarms do not survive reboot/process death; force a reschedule on every
                // service creation so boundary alarms are always re-registered.
                prefs.setAlarmsDirty(true)
                loop()
            }
        }
        Log.i(TAG, "foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE_TODAY -> lifecycleScope.launch(Dispatchers.Default) {
                workLock.withLock { pauseToday() }
            }
            ACTION_TURN_OFF_NOW -> lifecycleScope.launch(Dispatchers.Default) {
                workLock.withLock { turnOffNow() }
            }
            else -> wake.trySend(Unit)
        }
        return START_STICKY
    }

    private fun initialStatus() = StatusData(
        masterEnabled = true,
        paused = false,
        suppressed = false,
        capHitToday = false,
        hotspotOn = null,
        activeRoutineName = null,
        usageBytes = null,
        capMb = null,
        accessibilityOk = true
    )

    private suspend fun loop() {
        while (true) {
            try {
                workLock.withLock { tick() }
            } catch (t: Throwable) {
                Log.e(TAG, "tick failed", t)
                AttemptLog.add("tick failed: ${t.message}")
            }
            withTimeoutOrNull(TICK_MS) { wake.receive() }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val accOk = AccessibilityUtils.isServiceEnabled(applicationContext)
        if (!accOk) {
            val lastAlert = prefs.lastAccAlertMs.first()
            if (now - lastAlert > ACC_ALERT_COOLDOWN_MS) {
                notifications.notifyAccessibilityDisabled()
                prefs.setLastAccAlertMs(now)
            }
        }

        val master = prefs.masterEnabled.first()
        val paused = prefs.pausedUntilMs.first() > now
        val today = RoutineEvaluator.todayEpochDay(now, zone)
        val capHit = prefs.capHitEpochDay.first() == today
        val routines = repo.enabledRoutines()
        val active = RoutineEvaluator.activeRoutines(routines, now, zone)
        val capMb = RoutineEvaluator.strictestCapMb(active)
        val usage = usageMonitor.hotspotBytesSinceMidnight()
        usage?.let { repo.upsertUsageDay(today, it.bytes) }
        repo.pruneUsage(today - KEEP_DAYS)

        if (master && prefs.lastAppliedBoundary.first().isBlank()) {
            // Fresh install / wiped prefs: adopt current state as the baseline instead of
            // replaying a stale boundary from up to 3 days ago (which would toggle the
            // hotspot right after install).
            val last = RoutineEvaluator.lastBoundary(routines, now, zone)
            if (last != null) {
                prefs.setLastAppliedBoundary(last.key)
                AttemptLog.add("fresh install: seeded lastAppliedBoundary=${last.key}")
            }
        }

        if (master) {
            val last = RoutineEvaluator.lastBoundary(routines, now, zone)
            val lastApplied = prefs.lastAppliedBoundary.first()
            if (last != null && lastApplied != last.key) {
                // Replaying history is only correct for a recent gap. After a long absence
                // (phone off, app not installed) the single "last" boundary is a poor proxy for
                // intent and the hotspot can be stranded ON with no future boundary to end it.
                val appliedAt = RoutineEvaluator.boundaryAtMillis(lastApplied)
                val gapMs = if (appliedAt == null) Long.MAX_VALUE else now - appliedAt
                if (gapMs > CATCHUP_GAP_MS) {
                    reconcileToDesiredState(active, last.key, gapMs)
                } else {
                    applyBoundary(last, paused, capHit, capMb, usage)
                }
                alarmScheduler.rescheduleAll()
            } else if (prefs.alarmsDirty()) {
                // Survive reboots/process death: alarms are not persisted by the OS.
                alarmScheduler.rescheduleAll()
            }
        } else if (prefs.alarmsDirty()) {
            alarmScheduler.rescheduleAll()
        }

        if (master && !paused && !capHit && capMb != null) {
            if (usage == null) {
                // No usage access => no data at all, so the cap can never trip. Say so instead
                // of letting the user believe a limit is being enforced.
                AttemptLog.add("data cap not enforced: usage access not granted")
            } else {
                val bytes = usage.bytes
                if (bytes >= capMb * 1024L * 1024L) {
                    prefs.setCapHitEpochDay(today)
                    val result = controller.executeBoundary(
                        hotspotOn = false,
                        mobileDataTarget = null,
                        password = null
                    )
                    notifications.notifyCapReached(
                        Formatters.formatBytes(bytes),
                        Formatters.formatCapMb(capMb),
                        result.hotspot == ToggleResult.FAILED
                    )
                    AttemptLog.add("cap reached -> ${result.summary}")
                    Log.i(TAG, "cap reached usage=$bytes capMb=$capMb result=$result")
                }
            }
        }

        val suppressed = prefs.suppressedUntilNextWindow.first()
        val freshCapHit = prefs.capHitEpochDay.first() == today
        val hotspotKnown = controller.readHotspotState() ?: prefs.lastKnownHotspotOn.first()
        notifications.notifyStatus(
            StatusData(
                masterEnabled = master,
                paused = paused,
                suppressed = suppressed,
                capHitToday = freshCapHit,
                hotspotOn = hotspotKnown,
                activeRoutineName = active.firstOrNull()?.name,
                usageBytes = usage?.bytes,
                capMb = capMb,
                accessibilityOk = accOk
            )
        )
        WidgetProvider.updateAll(applicationContext, hotspotKnown, usage?.bytes, capMb)
    }

    /**
     * Long-gap recovery. Replaying a single stale boundary is meaningless when the device was
     * off for hours, so converge on what the *current* schedule says instead of what one old
     * boundary said. Only acts when the real state is observable and disagrees, so an unknown
     * state never causes a toggle.
     */
    private suspend fun reconcileToDesiredState(
        active: List<com.iranjan.hotspotscheduler.data.model.Routine>,
        boundaryKey: String,
        gapMs: Long
    ) {
        val target = active.isNotEmpty()
        val current = controller.readHotspotState()
        val gapText = if (gapMs == Long.MAX_VALUE) "unknown" else "${gapMs / 3_600_000}h"
        Log.i(TAG, "long gap ($gapText) since last applied boundary; reconciling to target=$target")
        AttemptLog.add("long gap (${gapText}) since last boundary; reconcile target=$target current=$current")
        if (current == null) {
            // Cannot observe the hotspot; do not guess. Record the boundary so the next tick
            // does not keep retrying this every 2 minutes.
            prefs.setLastAppliedBoundary(boundaryKey)
            return
        }
        if (current == target) {
            prefs.setLastAppliedBoundary(boundaryKey)
            return
        }
        val result = controller.executeBoundary(
            hotspotOn = target,
            mobileDataTarget = null,
            password = null
        )
        reportTransaction("long-gap reconcile", result)
        prefs.setLastAppliedBoundary(boundaryKey)
    }

    /**
     * Applies one scheduled boundary as a SINGLE transaction.
     *
     * Hotspot and mobile data are two steps of the same unattended job, not two jobs. Passing them
     * separately made the controller lock the phone between them, so the second step always ran
     * against a locked screen.
     */
    private suspend fun applyBoundary(
        boundary: RoutineEvaluator.Boundary,
        paused: Boolean,
        capHit: Boolean,
        capMb: Long?,
        usage: UsageSample?
    ) {
        val usageBytes = usage?.bytes ?: 0L
        val routine = repo.routine(boundary.routineId)
        if (boundary.isStart) {
            prefs.setSuppressedUntilNextWindow(false)
            val today = RoutineEvaluator.todayEpochDay(System.currentTimeMillis())
            val freshCapHit = prefs.capHitEpochDay.first() == today
            val allowed = !paused && !freshCapHit && (capMb == null || usageBytes < capMb * 1024L * 1024L)
            if (!allowed) {
                Log.i(TAG, "boundary START skipped paused=$paused capHit=$freshCapHit capMb=$capMb usage=$usageBytes")
                prefs.setLastAppliedBoundary(boundary.key)
                return
            }
            val result = controller.executeBoundary(
                hotspotOn = true,
                mobileDataTarget = if (routine?.mobileData == true) true else null,
                password = routine?.hotspotPassword
            )
            reportTransaction("START '${routine?.name}'", result)
        } else {
            // Only another active routine that wants data keeps it on.
            val othersNeedData = routine?.mobileData == true &&
                RoutineEvaluator.activeRoutines(
                    repo.enabledRoutines().filter { it.id != routine.id },
                    System.currentTimeMillis()
                ).any { it.mobileData }
            val dataTarget: Boolean? = when {
                routine?.mobileData != true -> null
                othersNeedData -> null
                else -> false
            }
            val result = controller.executeBoundary(
                hotspotOn = false,
                mobileDataTarget = dataTarget,
                password = null
            )
            reportTransaction("END '${routine?.name}'", result)
        }
        prefs.setLastAppliedBoundary(boundary.key)
    }

    /** Logs the whole transaction and raises an alert only for a real failure. */
    private fun reportTransaction(label: String, result: com.iranjan.hotspotscheduler.accessibility.TransactionResult) {
        Log.i(TAG, "transaction $label -> ${result.summary}")
        AttemptLog.add("transaction $label -> ${result.summary}")
        when {
            result.hotspot == ToggleResult.FAILED || result.mobileData == ToggleResult.FAILED ->
                notifications.notifyToggleFailed()
            result.anyFailure -> notifications.notifyUnlockRequired()
        }
    }

    private suspend fun pauseToday() {
        val midnight = RoutineEvaluator.nextMidnight(System.currentTimeMillis())
        prefs.setPausedUntilMs(midnight)
        Log.i(TAG, "paused until $midnight")
        wake.trySend(Unit)
    }

    private suspend fun turnOffNow() {
        val result = controller.executeBoundary(
            hotspotOn = false,
            mobileDataTarget = null,
            password = null
        )
        val now = System.currentTimeMillis()
        val active = RoutineEvaluator.activeRoutines(repo.enabledRoutines(), now)
        if (active.isNotEmpty()) {
            prefs.setSuppressedUntilNextWindow(true)
        }
        if (result.hotspot == ToggleResult.FAILED) {
            notifications.notifyToggleFailed()
        }
        Log.i(TAG, "turnOffNow result=$result suppressed=${active.isNotEmpty()}")
        wake.trySend(Unit)
    }

    companion object {
        private const val TAG = "HSAuto"
        private const val TICK_MS = 2 * 60 * 1000L
        private const val ACC_ALERT_COOLDOWN_MS = 60 * 60 * 1000L
        private const val KEEP_DAYS = 30L

        /** Beyond this gap, converge on the current schedule instead of replaying one boundary. */
        private const val CATCHUP_GAP_MS = 6 * 60 * 60 * 1000L

        const val ACTION_START = "com.iranjan.hotspotscheduler.START"
        const val ACTION_REFRESH = "com.iranjan.hotspotscheduler.REFRESH"
        const val ACTION_BOUNDARY = AlarmScheduler.ACTION_BOUNDARY
        const val ACTION_MIDNIGHT = AlarmScheduler.ACTION_MIDNIGHT
        const val ACTION_PAUSE_TODAY = "com.iranjan.hotspotscheduler.PAUSE_TODAY"
        const val ACTION_TURN_OFF_NOW = "com.iranjan.hotspotscheduler.TURN_OFF_NOW"

        fun start(context: android.content.Context, action: String, base: Intent? = null) {
            val intent = Intent(context, HotspotAutomationService::class.java).setAction(action)
            base?.let { intent.putExtras(it) }
            context.startForegroundService(intent)
        }
    }
}
