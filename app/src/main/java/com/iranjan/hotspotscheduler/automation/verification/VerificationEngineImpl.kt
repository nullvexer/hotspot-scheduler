package com.iranjan.hotspotscheduler.automation.verification

import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Feature
import com.iranjan.hotspotscheduler.platform.Result
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerificationEngineImpl @Inject constructor(
    private val strategyResolver: OperationStrategyResolver
) : VerificationEngine {

    override suspend fun verifyAll(desired: DesiredNetworkState): Boolean {
        val hotspotTarget = desired.hotspot
        val dataTarget = desired.mobileData

        val hotspotOk = when (hotspotTarget) {
            is com.iranjan.hotspotscheduler.domain.model.Target.Set -> {
                val strategy = strategyResolver.resolveHotspot()
                val result = strategy.verifyState(hotspotTarget.on)
                result is Result.Success && result.value
            }
            else -> true
        }

        val dataOk = when (dataTarget) {
            is com.iranjan.hotspotscheduler.domain.model.Target.Set -> {
                val strategy = strategyResolver.resolveMobileData()
                val result = strategy.verifyState(dataTarget.on)
                result is Result.Success && result.value
            }
            else -> true
        }

        return hotspotOk && dataOk
    }

    override suspend fun verifyFeature(feature: Feature, target: Boolean): Boolean {
        val strategy = when (feature) {
            Feature.HOTSPOT -> strategyResolver.resolveHotspot()
            Feature.MOBILE_DATA -> strategyResolver.resolveMobileData()
        }
        val result = strategy.verifyState(target)
        return result is Result.Success && result.value
    }
}