package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.automation.strategies.SamsungSettingsHotspotStrategy
import com.iranjan.hotspotscheduler.automation.strategies.SamsungSettingsDataStrategy
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationStrategyResolver @Inject constructor(
    private val hotspotStrategies: List<NetworkOperationStrategy>,
    private val dataStrategies: List<NetworkOperationStrategy>,
    private val accessibility: AccessibilityRuntime,
    private val screenSession: ScreenSession
) {

    fun resolveHotspot(): NetworkOperationStrategy = selectBest(hotspotStrategies, "hotspot")
    fun resolveMobileData(): NetworkOperationStrategy = selectBest(dataStrategies, "mobile data")

    private fun selectBest(strategies: List<NetworkOperationStrategy>, feature: String): NetworkOperationStrategy {
        for (strategy in strategies) {
            val probe = strategy.readState()
            if (probe is Result.Success) {
                AttemptLog.add("$feature: selected ${strategy::class.simpleName}")
                return strategy
            }
        }
        AttemptLog.add("$feature: no strategy available, using first as fallback")
        return strategies.firstOrNull() ?: throw IllegalStateException("no $feature strategies configured")
    }
}