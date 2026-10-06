package com.iranjan.hotspotscheduler.automation.transaction

import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.domain.model.AutomationResult

interface TransactionExecutor {
    suspend fun execute(session: AutomationSession): AutomationResult
}