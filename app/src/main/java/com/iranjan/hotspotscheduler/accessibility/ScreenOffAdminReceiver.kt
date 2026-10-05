package com.iranjan.hotspotscheduler.accessibility

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device administrator used for exactly one thing: [android.app.admin.DevicePolicyManager.lockNow],
 * which is the only public API that turns the screen off from an app.
 *
 * Declaring a receiver here adds a system-visible "Device administrator" entry. The app uses no
 * other device policy: no password policies, no wipe, no camera disable.
 */
class ScreenOffAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AttemptLog.add("device administrator enabled (used only to turn the screen off)")
        toast(context, "Device administrator enabled. The app can now turn the screen off.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AttemptLog.add("device administrator disabled; the app can no longer turn the screen off")
        toast(context, "Device administrator disabled. Automatic screen-off is off.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AttemptLog.add("device administrator removal requested")
        return "Disabling this removes the app's ability to turn the screen off after a scheduled " +
            "hotspot change. Scheduled toggles will still work, but the screen will stay on."
    }

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}