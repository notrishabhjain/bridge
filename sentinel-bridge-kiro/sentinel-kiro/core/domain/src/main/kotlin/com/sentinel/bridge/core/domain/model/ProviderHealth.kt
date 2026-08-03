package com.sentinel.bridge.core.domain.model

/**
 * Health status reported by an AI provider.
 *
 * Returned by [com.sentinel.bridge.core.domain.interfaces.AIProvider.health] to indicate
 * current operational state.
 */
sealed class ProviderHealth {

    /**
     * Provider is operational and ready to accept inference requests.
     *
     * @property modelName Name of the currently loaded model.
     * @property memoryUsageMb Approximate memory consumption in megabytes.
     */
    data class Healthy(
        val modelName: String,
        val memoryUsageMb: Long
    ) : ProviderHealth()

    /**
     * Provider is in a degraded state but may still respond.
     *
     * @property reason Human-readable explanation of the degradation.
     */
    data class Degraded(
        val reason: String
    ) : ProviderHealth()

    /**
     * Provider is unavailable and cannot process requests.
     *
     * @property reason Human-readable explanation of unavailability.
     */
    data class Unavailable(
        val reason: String
    ) : ProviderHealth()
}
