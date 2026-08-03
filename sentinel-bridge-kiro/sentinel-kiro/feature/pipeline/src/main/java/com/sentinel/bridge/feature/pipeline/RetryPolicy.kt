package com.sentinel.bridge.feature.pipeline

import kotlinx.coroutines.delay

/**
 * Implements exponential backoff retry logic for pipeline command execution.
 *
 * The delay between retries follows the formula: `baseDelayMs * 2^attempt`,
 * capped at [maxDelayMs]. With default values, the delay progression is:
 * 1s → 2s → 4s → 8s.
 *
 * This class is used internally by [BaseCommandHandler] to wrap the actual
 * command execution with retry semantics.
 *
 * @property maxRetries Maximum number of retry attempts (0 means execute once with no retries).
 * @property baseDelayMs Base delay in milliseconds before the first retry (default: 1000ms).
 * @property maxDelayMs Maximum delay cap in milliseconds (default: 8000ms).
 */
class RetryPolicy(
    private val maxRetries: Int,
    private val baseDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 8000L
) {

    /**
     * Executes the given [block] with exponential backoff retries.
     *
     * The block is attempted up to [maxRetries] + 1 times. On each failure,
     * the delay before the next attempt is calculated as:
     * `min(baseDelayMs * 2^attempt, maxDelayMs)`.
     *
     * @param T The return type of the block.
     * @param block The suspending operation to execute with retry semantics.
     * @return The result of the first successful execution of [block].
     * @throws Exception The last exception thrown by [block] after all retries are exhausted.
     */
    suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = (baseDelayMs * (1 shl attempt)).coerceAtMost(maxDelayMs)
                    delay(delayMs)
                }
                attempt++
            }
        }
        throw lastException!!
    }
}
