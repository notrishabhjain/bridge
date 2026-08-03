package com.sentinel.bridge.feature.setup

import com.sentinel.bridge.core.data.datastore.FeatureFlagsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates device capability profile on subsequent app launches after setup is complete.
 *
 * When [FeatureFlagsRepository.setupComplete] is `true`, this validator calls
 * [CapabilityManager.validateCapabilityProfile] to compare the current Recorder UI
 * against the stored [CapabilityProfileEntity]. If a mismatch is detected,
 * [CapabilityManager] broadcasts `CAPABILITY_MISMATCH` automatically.
 *
 * Usage: Call [validate] from the application startup path (e.g., [SentinelApplication.onCreate]
 * or an initializer) to perform the post-setup validation check.
 *
 * This class does NOT block the UI thread. It should be called from a coroutine scope.
 */
@Singleton
class LaunchValidator @Inject constructor(
    private val featureFlagsRepository: FeatureFlagsRepository,
    private val capabilityManager: CapabilityManager
) {

    /**
     * Performs post-launch capability validation if setup has been completed.
     *
     * Execution flow:
     * 1. Checks if `setupComplete == true` in DataStore.
     * 2. If not complete, returns [LaunchValidationResult.SetupNotComplete] (wizard should run).
     * 3. If complete, calls [CapabilityManager.validateCapabilityProfile].
     * 4. Returns [LaunchValidationResult.Valid] on match.
     * 5. Returns [LaunchValidationResult.Mismatch] on mismatch — the broadcast is already
     *    sent by [CapabilityManager.validateCapabilityProfile].
     * 6. Returns [LaunchValidationResult.NoProfile] if no stored profile exists.
     * 7. Returns [LaunchValidationResult.ServiceUnavailable] if the accessibility service
     *    is not connected (validation cannot be performed).
     *
     * @return A [LaunchValidationResult] describing the outcome.
     */
    suspend fun validate(): LaunchValidationResult {
        val isSetupComplete = featureFlagsRepository.setupComplete.first()

        if (!isSetupComplete) {
            return LaunchValidationResult.SetupNotComplete
        }

        return when (val result = capabilityManager.validateCapabilityProfile()) {
            is ProfileMatchResult.Match -> LaunchValidationResult.Valid
            is ProfileMatchResult.Mismatch -> LaunchValidationResult.Mismatch(result.reason)
            is ProfileMatchResult.NoStoredProfile -> LaunchValidationResult.NoProfile
            is ProfileMatchResult.ServiceNotConnected -> LaunchValidationResult.ServiceUnavailable
        }
    }
}

/**
 * Result of the post-launch capability validation.
 */
sealed class LaunchValidationResult {

    /**
     * The stored capability profile matches the current device state.
     * The pipeline can operate normally.
     */
    object Valid : LaunchValidationResult()

    /**
     * The stored capability profile does not match the current device state.
     * A `CAPABILITY_MISMATCH` broadcast has already been sent by [CapabilityManager].
     *
     * @property reason Human-readable explanation of the mismatch.
     */
    data class Mismatch(val reason: String) : LaunchValidationResult()

    /**
     * No capability profile has been recorded yet.
     * The setup wizard should be run again.
     */
    object NoProfile : LaunchValidationResult()

    /**
     * The accessibility service is not connected, so validation cannot be performed.
     * The user may need to re-enable the service.
     */
    object ServiceUnavailable : LaunchValidationResult()

    /**
     * Setup has not been completed yet. The [SetupWizardActivity] should be launched.
     */
    object SetupNotComplete : LaunchValidationResult()
}
