package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.iranjan.hotspotscheduler.accessibility.ScreenControl.DismissOutcome
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Invisible host activity for the two things an app cannot do from the background.
 *
 * 1. **Dismiss the keyguard.** `KeyguardManager.requestDismissKeyguard()` requires an Activity, and
 *    the official contract is: a non-secure keyguard, *or a device in a trusted state* (Smart Lock,
 *    Extend Unlock / trusted places), is dismissed immediately with no user interaction. A secure
 *    keyguard outside a trusted state brings up the credential UI, which is the correct outcome.
 * 2. **Start another Activity.** Android blocks background activity starts, so Settings is opened
 *    from this already-visible Activity instead.
 *
 * The activity renders nothing and finishes as soon as it is done; it exists purely to give the
 * platform a foreground context.
 */
@AndroidEntryPoint
class ToggleHostActivity : ComponentActivity() {

    @Inject
    lateinit var screen: ScreenControl

    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (finished) return
        finished = true
        when (val action = intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_DISMISS_KEYGUARD -> dismissKeyguard()
            ACTION_LAUNCH -> launchTarget()
            else -> finish()
        }
    }

    private fun dismissKeyguard() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null) {
            report(DismissOutcome.UNKNOWN)
            return
        }
        if (!km.isKeyguardLocked) {
            report(DismissOutcome.DISMISSED)
            return
        }
        // API 26+ path: handles the trusted-state case and reports the real outcome.
        km.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    report(DismissOutcome.DISMISSED)
                }

                override fun onDismissError() {
                    report(DismissOutcome.SECURED)
                }
            }
        )
        // Safety net: if the callback never arrives (activity recreated), do not hang the caller.
        window.decorView.postDelayed({ if (!isFinishing) finish() }, DISMISS_WATCHDOG_MS)
    }

    private fun launchTarget() {
        val target = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val component = intent?.getStringExtra(EXTRA_TARGET_CLASS)
        if (target == null || component == null) {
            AttemptLog.add("host activity: no launch target supplied")
            finish()
            return
        }
        val launched = try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(target, component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            true
        } catch (t: Throwable) {
            AttemptLog.add("host activity: startActivity failed: ${t.message}")
            false
        }
        setResult(if (launched) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun report(outcome: DismissOutcome) {
        setResult(
            when (outcome) {
                DismissOutcome.DISMISSED -> RESULT_OK
                DismissOutcome.SECURED -> RESULT_SECURED
                DismissOutcome.UNKNOWN -> RESULT_CANCELED
            }
        )
        AttemptLog.add("keyguard dismiss outcome=$outcome")
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_TARGET_CLASS = "target_class"
        const val ACTION_DISMISS_KEYGUARD = "dismiss_keyguard"
        const val ACTION_LAUNCH = "launch"
        private const val DISMISS_WATCHDOG_MS = 12_000L
        const val RESULT_OK = ActivityResult.OK
        const val RESULT_SECURED = ActivityResult.SECURED
        const val RESULT_CANCELED = ActivityResult.CANCELED

        fun dismissIntent(context: Context): Intent =
            Intent(context, ToggleHostActivity::class.java)
                .putExtra(EXTRA_ACTION, ACTION_DISMISS_KEYGUARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun launchIntent(context: Context, pkg: String, cls: String): Intent =
            Intent(context, ToggleHostActivity::class.java)
                .putExtra(EXTRA_ACTION, ACTION_LAUNCH)
                .putExtra(EXTRA_TARGET_PACKAGE, pkg)
                .putExtra(EXTRA_TARGET_CLASS, cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        /** Kept separate from android.app.Activity.RESULT_* so the meanings stay readable. */
        private object ActivityResult {
            const val OK = -1        // Activity.RESULT_OK
            const val CANCELED = 0  // Activity.RESULT_CANCELED
            const val SECURED = 2   // custom: a credential is required
        }
    }
}
