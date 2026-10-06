package com.iranjan.hotspotscheduler.automation.strategies

import com.iranjan.hotspotscheduler.domain.model.StepResult
import com.iranjan.hotspotscheduler.platform.Result

/**
 * One way of driving a single network feature.
 *
 * The resolver probes the available implementations in order and keeps the first that reports it
 * can read state, so a device that cannot serve the primary path still has a fallback. An
 * implementation must never report success for a change it did not observe: [setState] is followed
 * by [verifyState], and the transaction treats an unverified result as a failure.
 */
interface NetworkOperationStrategy {

    /** Human-readable name for the diagnostics log. */
    val name: String

    /** Reads the feature's current state, or null when it genuinely cannot be determined. */
    suspend fun readState(): Result<Boolean>

    /**
     * Drives the feature towards [target]. Returns [StepResult.ALREADY_OK] when the observed state
     * already matches, so an idempotent re-run performs no UI mutation at all.
     */
    suspend fun setState(target: Boolean, password: String?): Result<StepResult>

    /** Re-reads the feature and confirms it matches [target]. */
    suspend fun verifyState(target: Boolean): Result<Boolean>
}