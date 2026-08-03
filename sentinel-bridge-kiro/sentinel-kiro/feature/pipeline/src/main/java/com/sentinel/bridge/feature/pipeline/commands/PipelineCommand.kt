package com.sentinel.bridge.feature.pipeline.commands

/**
 * Represents all discrete stages in the Sentinel pipeline.
 *
 * Each subclass corresponds to a single pipeline step executed by the [CommandBus].
 * The orchestrator emits commands sequentially; each command is dispatched to its
 * dedicated [CommandHandler] implementation via Hilt multibinding.
 *
 * Commands are the sole communication mechanism between the orchestrator and handlers.
 * Direct calls, reflection, and EventBus are prohibited.
 */
sealed class PipelineCommand {

    /**
     * Opens the Xiaomi Recorder application via explicit Intent.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class OpenRecorder(val sessionId: String) : PipelineCommand()

    /**
     * Navigates to the most recent recording entry in the Recorder UI.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class OpenRecording(val sessionId: String) : PipelineCommand()

    /**
     * Clicks the "Show text" button to initiate transcription in the Recorder.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class ClickShowText(val sessionId: String) : PipelineCommand()

    /**
     * Selects the target transcription language from the Recorder's language picker.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     * @property language Language code to select (e.g., "Hindi").
     */
    data class SelectLanguage(val sessionId: String, val language: String) : PipelineCommand()

    /**
     * Waits for the "Finished transcribing" notification from Xiaomi Recorder.
     *
     * Subscribes to [SentinelNotificationListener] and applies the configured timeout.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     * @property timeoutMs Maximum time in milliseconds to wait for transcription completion.
     */
    data class WaitForTranscription(val sessionId: String, val timeoutMs: Long) : PipelineCommand()

    /**
     * Extracts transcript text nodes from the Recorder's accessibility tree.
     *
     * Skips UI controls, timestamps, AI summary cards, and speaker chips.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class ExtractTranscript(val sessionId: String) : PipelineCommand()

    /**
     * Runs the text preprocessor on the raw transcript before rules evaluation.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class RunPreprocessor(val sessionId: String) : PipelineCommand()

    /**
     * Evaluates Pre-AI rules from the Rules Engine (Phase 1).
     *
     * May result in IGNORE (skip pipeline) or REJECT (fail pipeline) decisions.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class RunRulesPreAI(val sessionId: String) : PipelineCommand()

    /**
     * Builds the inference prompt by loading the versioned template and injecting variables.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class BuildPrompt(val sessionId: String) : PipelineCommand()

    /**
     * Runs local LLM inference via [LlamaCppProvider] with the constructed prompt.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class RunInference(val sessionId: String) : PipelineCommand()

    /**
     * Parses the raw LLM output string into Kotlin domain objects.
     *
     * Strips markdown fences and extracts the JSON payload.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class ParseResponse(val sessionId: String) : PipelineCommand()

    /**
     * Validates the parsed JSON against the output schema.
     *
     * Attempts one repair pass (strip fences, fix trailing commas, escape chars) if invalid.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class ValidateJson(val sessionId: String) : PipelineCommand()

    /**
     * Evaluates Post-AI rules from the Rules Engine (Phase 2).
     *
     * Normalizes output and rejects low-confidence results.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class RunRulesPostAI(val sessionId: String) : PipelineCommand()

    /**
     * Persists the validated pipeline result to Room and file storage.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class StoreResult(val sessionId: String) : PipelineCommand()

    /**
     * Dispatches the final action (e.g., broadcast result to MacroDroid).
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class DispatchAction(val sessionId: String) : PipelineCommand()

    /**
     * Returns the pipeline result Intent to MacroDroid with session metadata.
     *
     * Broadcasts PIPELINE_COMPLETE or PIPELINE_FAILED with required extras.
     *
     * @property sessionId Unique identifier for the current pipeline session.
     */
    data class ReturnIntent(val sessionId: String) : PipelineCommand()
}
