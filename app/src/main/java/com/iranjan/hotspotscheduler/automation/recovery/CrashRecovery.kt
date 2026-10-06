package com.iranjan.hotspotscheduler.automation.recovery

import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashRecovery @Inject constructor(
    private val reconciliationEngine: ReconciliationEngineImpl,
    private val logger: AutomationLogger
) {

    private val mutex = Mutex()
    private var hasRun = false

    suspend fun runIfNeeded(): AutomationResult? = mutex.withLock {
        if (hasRun) return@withLock null
        hasRun = true

        logger.logAction("crash_recovery", mapOf("trigger" to "app_start"))
        return@withLock reconciliationEngine.reconcileAfterCrash()
    }

    fun reset() {
        mutex.withLock { hasRun = false }
    }
}