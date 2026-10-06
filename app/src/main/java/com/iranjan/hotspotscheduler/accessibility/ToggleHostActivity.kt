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
import com.iranjan.hotspotscheduler.util.AttemptLog

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
        if (!km.isKeyguardSecure) {
            km.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = finish()
                    override fun onDismissError() = finish()
                }
            )
        }
        window.decorView.postDelayed({ if (!isFinishing) finish() }, DISMISS_WATCHDOG_MS)
    }

    private fun launchTarget() {
        val pkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val cls = intent?.getStringExtra(EXTRA_TARGET_CLASS)
        if (pkg != null && cls != null) {
            try {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                AttemptLog.add("host activity could not start $pkg/$cls: ${e.message}")
            }
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