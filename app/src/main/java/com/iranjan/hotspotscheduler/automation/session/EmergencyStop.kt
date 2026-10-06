package com.iranjan.hotspotscheduler.automation.session

import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyStop @Inject constructor(
    private val logger: AutomationLogger
) {

    private val mutex = Mutex()
    @Volatile
    private var stopped = false

    fun stop() {
        mutex.withLock {
            stopped = true
            logger.logAction("emergency_stop", mapOf("trigger" to "user"))
        }
    }

    fun resume() {
        mutex.withLock {
            stopped = false
            logger.logAction("emergency_stop", mapOf("trigger" to "resume"))
        }
    }

    fun isStopped(): Boolean = stopped

    suspend fun checkAndThrow() {
        mutex.withLock {
            if (stopped) {
                throw EmergencyStopException("Automation stopped by user")
            }
        }
    }
}

class EmergencyStopException(message: String) : Exception(message)