package com.sentinel.bridge.core.domain.model

/**
 * Outcome of dispatching a pipeline result to an action provider.
 *
 * Returned by [com.sentinel.bridge.core.domain.interfaces.ActionProvider.dispatch] to
 * indicate whether the action was successfully delivered.
 */
sealed class ActionOutcome {

    /**
     * Action was dispatched successfully.
     *
     * @property actionId Identifier of the action that was dispatched.
     */
    data class Success(
        val actionId: String
    ) : ActionOutcome()

    /**
     * Action dispatch failed.
     *
     * @property actionId Identifier of the action that failed.
     * @property reason Human-readable explanation of the failure.
     * @property retryable Whether the action may succeed if retried.
     */
    data class Failure(
        val actionId: String,
        val reason: String,
        val retryable: Boolean
    ) : ActionOutcome()
}
