package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.platform.Result
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.util.AttemptLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the strategy used to drive each network feature.
 *
 * The choice is made once per feature and then cached for the life of the process. That matters
 * because probing is not free: the Quick Settings implementations have to open the shade to read a
 * tile, so re-probing on every call inside a transaction would keep reopening it and would fight
 * the Settings screen the transaction is already driving.
 *
 * Probe order is the order the caller supplies, which is least-fragile-first: the explicit
 * Settings screen is tried before the Quick Settings tile.
 */
@Singleton
class OperationStrategyResolver @Inject constructor(
    private val hotspotStrategies: List<NetworkOperationStrategy>,
    private val dataStrategies: List<NetworkOperationStrategy>,
    private val accessibility: AccessibilityRuntime
) {

    private val lock = Mutex()
    private var cachedHotspot: NetworkOperationStrategy? = null
    private var cachedData: NetworkOperationStrategy? = null

    suspend fun resolveHotspot(): NetworkOperationStrategy {
        cachedHotspot?.let { return it }
        return lock.withLock {
            cachedHotspot ?: selectBest(hotspotStrategies, "hotspot").also { cachedHotspot = it }
        }
    }

    suspend fun resolveMobileData(): NetworkOperationStrategy {
        cachedData?.let { return it }
        return lock.withLock {
            cachedData ?: selectBest(dataStrategies, "mobile data").also { cachedData = it }
        }
    }

    /**
     * The first strategy that can actually read state wins. If none can, the first is returned so
     * the caller gets a precise failure from that strategy rather than a bare "no strategy".
     */
    private suspend fun selectBest(
        strategies: List<NetworkOperationStrategy>,
        feature: String
    ): NetworkOperationStrategy {
        require(strategies.isNotEmpty()) { "no strategies registered for $feature" }
        for (strategy in strategies) {
            val probe = strategy.readState()
            if (probe is Result.Success<*>) {
                AttemptLog.add("$feature: using ${strategy.name}")
                return strategy
            }
        }
        AttemptLog.add("$feature: no strategy could read state; falling back to ${strategies.first().name}")
        return strategies.first()
    }
}