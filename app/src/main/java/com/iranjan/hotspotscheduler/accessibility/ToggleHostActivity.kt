package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

/**
 * Invisible host activity for the two things an app cannot do from the background.
 *
 * 1. **Dismiss the keyguard.** `KeyguardManager.requestDismissKeyguard()` requires an Activity. Its
 *    contract: a non-secure keyguard, *or a device in a trusted state* (Smart Lock, Extend Unlock,
 *    trusted places), is dismissed immediately with no user interaction.
 * 2. **Start another Activity.** Android blocks background activity starts, so Settings is opened
 *    from this already-visible Activity instead.
 *
 * It renders nothing and finishes as soon as it is done; it exists purely to give the platform a
 * foreground context. There is no result reporting: [ScreenControl] reads the real keyguard state
 * afterwards, which is more reliable than trusting an activity result.
 */
class ToggleHostActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
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
        if (handled) return
        handled = true
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_WAKE -> wakeOnly()
            ACTION_DISMISS_KEYGUARD -> dismissKeyguard()
            ACTION_LAUNCH -> launchTarget()
            else -> finish()
        }
    }

    /**
     * Pure wake. Resuming this activity with setTurnScreenOn(true) is the documented way to bring
     * the display up, and it works whether or not a secure keyguard is present - which the wake
     * lock path alone does not guarantee on every OEM build.
     */
    private fun wakeOnly() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.postDelayed({ if (!isFinishing) finish() }, WAKE_LINGER_MS)
    }

    private fun dismissKeyguard() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) {
            finish()
            return
        }
        // Only meaningful for a non-secure keyguard; a secure one outside a trusted state raises
        // the credential UI, which is the correct outcome and is handled by the PIN-pad path.
        if (!km.isKeyguardSecure) {
            km.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = finish()
                    override fun onDismissError() = finish()
                }
            )
        }
        // Never hang: if neither callback arrives, close so the caller's poll can continue.
        window.decorView.postDelayed({ if (!isFinishing) finish() }, DISMISS_WATCHDOG_MS)
    }

    private fun launchTarget() {
        val pkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val cls = intent?.getStringExtra(EXTRA_TARGET_CLASS)
        if (pkg != null && cls != null) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }.onFailure { AttemptLog.add("host activity could not start $pkg/$cls: ${it.message}") }
        } else {
            AttemptLog.add("host activity: no launch target supplied")
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_TARGET_CLASS = "target_class"
        const val ACTION_DISMISS_KEYGUARD = "dismiss_keyguard"
        const val ACTION_LAUNCH = "launch"
        const val ACTION_WAKE = "wake"
        private const val DISMISS_WATCHDOG_MS = 12_000L

        /** Stay visible briefly so the display actually comes up before we finish. */
        private const val WAKE_LINGER_MS = 1_200L

        fun wakeIntent(context: android.content.Context): Intent =
            Intent(context, ToggleHostActivity::class.java)
                .putExtra(EXTRA_ACTION, ACTION_WAKE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun dismissIntent(context: android.content.Context): Intent =
            Intent(context, ToggleHostActivity::class.java)
                .putExtra(EXTRA_ACTION, ACTION_DISMISS_KEYGUARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun launchIntent(context: android.content.Context, pkg: String, cls: String): Intent =
            Intent(context, ToggleHostActivity::class.java)
                .putExtra(EXTRA_ACTION, ACTION_LAUNCH)
                .putExtra(EXTRA_TARGET_PACKAGE, pkg)
                .putExtra(EXTRA_TARGET_CLASS, cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
