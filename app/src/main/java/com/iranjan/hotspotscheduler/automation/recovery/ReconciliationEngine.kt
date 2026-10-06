package com.iranjan.hotspotscheduler.automation.recovery

import com.iranjan.hotspotscheduler.domain.model.ActualNetworkState
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState

interface ReconciliationEngine {
    suspend fun reconcile(actual: ActualNetworkState, desired: DesiredNetworkState): AutomationResult
}