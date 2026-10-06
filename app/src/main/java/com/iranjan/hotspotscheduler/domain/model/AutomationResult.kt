package com.iranjan.hotspotscheduler.domain.model

enum class StepResult { ALREADY_OK, CHANGED, FAILED, BLOCKED }
enum class LockResult { LOCKED, ALREADY_LOCKED, FAILED, SKIPPED }

sealed class FailureReason {
    data class ServiceUnavailable(val detail: String) : FailureReason()
    data class OverlayUnavailable(val detail: String) : FailureReason()
    data class ExactAlarmUnavailable(val detail: String) : FailureReason()
    data class ScreenWakeFailed(val detail: String) : FailureReason()
    data class KeyguardStateUnknown(val detail: String) : FailureReason()
    data class KeypadNotFound(val detail: String) : FailureReason()
    data class KeypadGeometryInvalid(val detail: String) : FailureReason()
    data class CredentialNotConfigured(val detail: String) : FailureReason()
    data class CredentialEntryFailed(val detail: String) : FailureReason()
    data class UnlockVerificationFailed(val detail: String) : FailureReason()
    data class SettingsScreenNotFound(val detail: String) : FailureReason()
    data class ToggleNotFound(val feature: Feature, val detail: String) : FailureReason()
    data class ToggleActionRejected(val feature: Feature, val detail: String) : FailureReason()
    data class ToggleStateDidNotChange(val feature: Feature, val detail: String) : FailureReason()
    data class FinalStateMismatch(val feature: Feature, val expected: Boolean, val actual: Boolean?) : FailureReason()
    data class UnexpectedLock(val detail: String) : FailureReason()
    data class FinalLockFailed(val detail: String) : FailureReason()
    data class AutomationTimeout(val stage: String, val detail: String) : FailureReason()
    data class ConflictingRoutine(val detail: String) : FailureReason()
    data class DeviceProfileMismatch(val detail: String) : FailureReason()
    data class CapabilityProbeFailed(val capability: String, val detail: String) : FailureReason()
}

sealed class BlockReason {
    data class Paused(val until: Long) : BlockReason()
    data class CapReached(val capMb: Long, val usageMb: Long) : BlockReason()
    data class SuppressedUntilNextWindow : BlockReason()
    data class MasterDisabled : BlockReason()
    data class CredentialRequired : BlockReason()
}

sealed class AutomationResult {
    data class Success(
        val steps: List<StepResult>,
        val finalLock: LockResult,
        val sessionId: String
    ) : AutomationResult()

    data class PartialSuccess(
        val steps: List<StepResult>,
        val finalLock: LockResult,
        val failedFeature: Feature,
        val failureReason: FailureReason,
        val sessionId: String
    ) : AutomationResult()

    data class Failed(
        val reason: FailureReason,
        val partialSteps: List<StepResult> = emptyList(),
        val sessionId: String
    ) : AutomationResult()

    data class Blocked(
        val reason: BlockReason,
        val sessionId: String
    ) : AutomationResult()

    val isSuccessful: Boolean
        get() = this is Success

    val isTerminal: Boolean
        get() = this !is Blocked
}