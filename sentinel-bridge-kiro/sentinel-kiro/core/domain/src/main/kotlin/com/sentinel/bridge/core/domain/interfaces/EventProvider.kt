package com.sentinel.bridge.core.domain.interfaces

import android.content.Intent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InputContext

/**
 * Plugin interface for event source providers.
 *
 * Implementations translate incoming Android Intents into the universal [InputContext]
 * that flows through the pipeline. Each provider handles a specific [EventSource] type
 * (calls, notifications, manual triggers).
 */
interface EventProvider {

    /**
     * The event source type this provider handles.
     */
    val sourceType: EventSource

    /**
     * Determines whether this provider can process the given intent.
     *
     * @param intent The incoming Android Intent to evaluate.
     * @return `true` if this provider recognizes and can handle the intent.
     */
    fun canHandle(intent: Intent): Boolean

    /**
     * Builds a complete [InputContext] from the triggering intent.
     *
     * Extracts all relevant extras, metadata, and context from the intent
     * to construct the universal pipeline input contract.
     *
     * @param intent The incoming Android Intent to extract context from.
     * @return Fully populated [InputContext] ready for pipeline processing.
     */
    suspend fun buildInputContext(intent: Intent): InputContext
}
