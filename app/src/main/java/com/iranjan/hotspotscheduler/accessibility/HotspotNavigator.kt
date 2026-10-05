package com.iranjan.hotspotscheduler.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which Settings activity hosts the hotspot and data-usage screens on this device/ROM,
 * then opens it through [ScreenControl] (which goes via a foreground host activity, because Android
 * blocks background activity starts).
 */
@Singleton
class HotspotNavigator @Inject constructor(@ApplicationContext private val context: Context) {

    private val hotspotCandidates = listOf(
        ComponentName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApSettings"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApSettings"),
        ComponentName("com.android.settings", "com.android.settings.wifi.tether.WifiTetherSettings"),
        ComponentName("com.android.settings", "com.android.settings.TetherSettings"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.TetherSettings")
    )

    private val dataUsageCandidates = listOf(
        ComponentName("com.android.settings", "com.android.settings.Settings\$DataUsageSummaryActivity"),
        ComponentName("com.android.settings", "com.android.settings.datausage.DataUsageSettings"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.datausage.DataUsageSettings")
    )

    private fun resolvable(candidates: List<ComponentName>): List<ComponentName> {
        val pm = context.packageManager
        val result = mutableListOf<ComponentName>()
        for (cn in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(cn)
            try {
                if (pm.resolveActivity(intent, 0) != null) result.add(cn)
            } catch (t: Throwable) {
                // A candidate that cannot be resolved is simply skipped.
            }
        }
        return result
    }

    private fun firstResolvable(candidates: List<ComponentName>, action: String): ComponentName? {
        resolvable(candidates).firstOrNull()?.let { return it }
        // No explicit component resolved: fall back to the generic Settings screen.
        val implicit = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.packageManager.resolveActivity(implicit, 0)?.let {
                val info = it.activityInfo
                ComponentName(info.packageName, info.name)
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun hotspotTarget(): ComponentName? =
        firstResolvable(hotspotCandidates, Settings.ACTION_WIRELESS_SETTINGS)

    fun dataUsageTarget(): ComponentName? =
        firstResolvable(dataUsageCandidates, Settings.ACTION_DATA_USAGE_SETTINGS)

    suspend fun launchHotspotSettings(screen: ScreenControl): Boolean {
        val target = hotspotTarget()
        if (target == null) {
            AttemptLog.add("no hotspot Settings activity could be resolved on this device")
            return false
        }
        return screen.launchSettings(target.packageName, target.className)
    }

    suspend fun launchDataUsageSettings(screen: ScreenControl): Boolean {
        val target = dataUsageTarget()
        if (target == null) {
            AttemptLog.add("no data-usage Settings activity could be resolved on this device")
            return false
        }
        return screen.launchSettings(target.packageName, target.className)
    }
}