package com.iranjan.hotspotscheduler.automation.strategies

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.NodeSelector
import com.iranjan.hotspotscheduler.platform.accessibility.UiDump
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsNavigator @Inject constructor(@androidx.hilt.android.qualifiers.ApplicationContext private val context: Context) {

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

    fun hotspotTarget(): ComponentName? = firstResolvable(hotspotCandidates, Settings.ACTION_WIRELESS_SETTINGS)
    fun dataUsageTarget(): ComponentName? = firstResolvable(dataUsageCandidates, Settings.ACTION_DATA_USAGE_SETTINGS)

    private fun firstResolvable(candidates: List<ComponentName>, action: String): ComponentName? {
        val pm = context.packageManager
        for (cn in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(cn)
            try {
                if (pm.resolveActivity(intent, 0) != null) return cn
            } catch (e: Exception) { /* ignore */ }
        }
        val implicit = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            pm.resolveActivity(implicit, 0)?.let { info -> ComponentName(info.packageName, info.name) }
        } catch (e: Exception) { null }
    }
}

interface NavigationStrategy {
    suspend fun navigateToHotspot(accessibility: AccessibilityRuntime): Result<Unit>
    suspend fun navigateToMobileData(accessibility: AccessibilityRuntime): Result<Unit>
}

@Singleton
class SamsungSettingsNavigationStrategy @Inject constructor(
    private val navigator: SettingsNavigator
) : NavigationStrategy {

    override suspend fun navigateToHotspot(accessibility: AccessibilityRuntime): Result<Unit> {
        val target = navigator.hotspotTarget() ?: return Result.failure(Result.Error.NotAvailable("no hotspot target"))
        return navigateTo(accessibility, target)
    }

    override suspend fun navigateToMobileData(accessibility: AccessibilityRuntime): Result<Unit> {
        val target = navigator.dataUsageTarget() ?: return Result.failure(Result.Error.NotAvailable("no data usage target"))
        return navigateTo(accessibility, target)
    }

    private suspend fun navigateTo(accessibility: AccessibilityRuntime, target: ComponentName): Result<Unit> {
        val selector = NodeSelector(packageNames = listOf(target.packageName))
        val result = accessibility.waitForCondition(
            { accessibility.findNodes(selector) is Result.Success },
            java.time.Duration.ofSeconds(10)
        )
        return if (result is Result.Success && result.value) Result.success(Unit)
        else Result.failure(Result.Error.Timeout("target screen not detected"))
    }
}