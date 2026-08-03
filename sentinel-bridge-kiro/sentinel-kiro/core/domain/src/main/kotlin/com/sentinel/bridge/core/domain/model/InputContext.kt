package com.sentinel.bridge.core.domain.model

import java.time.Instant

/**
 * Universal pipeline input contract carrying all context needed for AI inference.
 *
 * Constructed by [com.sentinel.bridge.core.domain.interfaces.EventProvider] from the
 * triggering event. Flows through every pipeline stage unchanged.
 *
 * @property sessionId Unique identifier for this pipeline run.
 * @property source The event origin that triggered processing.
 * @property rawContent Raw transcript or notification text extracted from the source.
 * @property language BCP-47 language tag of the content (e.g., "hi", "en").
 * @property timestamp When the event was received.
 * @property conversationId Optional grouping key for related events.
 * @property metadata Arbitrary key-value pairs (callerName, phoneNumber, duration, etc.).
 * @property attachments List of additional input attachments.
 * @property capabilityProfileVersion Version of the device capability profile used.
 * @property recorderStrategy Name of the recorder automation strategy applied.
 * @property pipelineVersion Semantic version of the pipeline definition.
 */
data class InputContext(
    val sessionId: String,
    val source: EventSource,
    val rawContent: String,
    val language: String,
    val timestamp: Instant,
    val conversationId: String?,
    val metadata: Map<String, String>,
    val attachments: List<InputAttachment>,
    val capabilityProfileVersion: Int,
    val recorderStrategy: String,
    val pipelineVersion: Int
)
