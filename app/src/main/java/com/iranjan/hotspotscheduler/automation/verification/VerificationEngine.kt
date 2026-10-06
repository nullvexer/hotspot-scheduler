package com.iranjan.hotspotscheduler.automation.verification

import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Feature

interface VerificationEngine {
    suspend fun verifyAll(desired: DesiredNetworkState): Boolean
    suspend fun verifyFeature(feature: Feature, target: Boolean): Boolean
}