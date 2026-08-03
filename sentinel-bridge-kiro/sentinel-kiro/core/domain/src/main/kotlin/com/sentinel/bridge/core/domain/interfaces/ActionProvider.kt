package com.sentinel.bridge.core.domain.interfaces

import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult

/**
 * Plugin interface for action dispatch providers.
 *
 * Implementations deliver pipeline results to external systems (e.g., MacroDroid
 * via broadcast Intents). Each provider declares which event sources it can handle.
 */
interface ActionProvider {

    /**
     * Unique identifier for this action provider.
     */
    val id: String

    /**
     * Determines whether this provider can dispatch results for the given event source.
     *
     * @param source The event source type to check.
     * @return `true` if this provider handles dispatching for the given source.
     */
    fun canHandle(source: EventSource): Boolean

    /**
     * Dispatches the pipeline result to the external system.
     *
     * @param result The processed pipeline result to deliver.
     * @param context The original input context for reference during dispatch.
     * @return [ActionOutcome] indicating success or failure of the dispatch.
     */
    suspend fun dispatch(result: PipelineResult, context: InputContext): ActionOutcome
}
