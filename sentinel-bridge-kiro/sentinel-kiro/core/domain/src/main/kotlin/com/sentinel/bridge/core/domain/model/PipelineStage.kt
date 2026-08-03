package com.sentinel.bridge.core.domain.model

/**
 * Represents every discrete stage in the Sentinel pipeline state machine.
 *
 * The pipeline progresses linearly through these stages. Each transition
 * persists [currentStage] to Room before proceeding, enabling WorkManager
 * to resume from the correct point after a process restart.
 */
enum class PipelineStage {

    /** No pipeline is active. */
    IDLE,

    /** An incoming START_PIPELINE intent has been received and validated. */
    RECEIVED_INTENT,

    /** A new [PipelineSession] has been created and persisted to Room. */
    PIPELINE_CREATED,

    /** Pre-flight capability checks are running. */
    CAPABILITY_CHECK,

    /** Opening the Xiaomi Recorder app. */
    OPEN_RECORDER,

    /** Navigating to the latest recording inside Recorder. */
    OPEN_RECORDING,

    /** Tapping the "Show Text" button to trigger transcription display. */
    CLICK_SHOW_TEXT,

    /** Selecting the target language for transcription. */
    SELECT_LANGUAGE,

    /** Waiting for the transcription-complete notification from HyperAI. */
    WAIT_TRANSCRIPTION,

    /** Reading transcript text nodes from the Recorder accessibility tree. */
    EXTRACT_TRANSCRIPT,

    /** Running text preprocessing (normalization, trimming). */
    PREPROCESS,

    /** Evaluating pre-AI rules (ignore/reject decisions). */
    RULES_PRE,

    /** Building the LLM prompt from template and variables. */
    BUILD_PROMPT,

    /** Loading the on-device LLM model into memory. */
    LOAD_MODEL,

    /** Running LLM inference to generate structured output. */
    INFERENCE,

    /** Parsing the raw LLM text output into domain objects. */
    PARSE_RESPONSE,

    /** Validating (and optionally repairing) the JSON output against schema. */
    VALIDATE_JSON,

    /** Evaluating post-AI rules (confidence filtering, normalization). */
    RULES_POST,

    /** Persisting the final pipeline result to Room and file storage. */
    STORE_RESULT,

    /** Dispatching the result to the appropriate ActionProvider. */
    DISPATCH_ACTION,

    /** Sending the PIPELINE_COMPLETE or PIPELINE_FAILED intent back to MacroDroid. */
    RETURN_INTENT,

    /** Pipeline has finished successfully. Terminal state. */
    COMPLETE
}
