package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.PipelineResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raised when a stage needs a value an earlier stage was supposed to produce, but
 * that value is absent.
 *
 * Stages must fail on missing state rather than substituting a default. Passing an
 * empty transcript to the model, or an empty string to the response parser, produces
 * a run that reports success while doing nothing — which is far harder to diagnose
 * than an explicit failure.
 *
 * @property sessionId Session whose state was incomplete.
 * @property field Name of the missing value.
 */
class MissingSessionStateException(
    val sessionId: String,
    val field: String
) : IllegalStateException(
    "Session $sessionId has no '$field'. The stage that produces it did not run, " +
        "or its state was lost (for example if the process was killed mid-pipeline)."
)

/**
 * Values handed from one pipeline stage to the next within a single run.
 *
 * @property transcript Conversation text to analyse, from the recorder or manual entry.
 * @property language Language of [transcript], used to render the prompt.
 * @property renderedPrompt Prompt produced by the build-prompt stage.
 * @property promptVersion Version of the prompt template used.
 * @property model Model identifier the prompt targets.
 * @property rawResponse Unparsed model output.
 * @property result Structured result parsed from [rawResponse].
 * @property startedAtMs Epoch millis when the run began, for processing-time reporting.
 */
data class PipelineSessionState(
    val transcript: String? = null,
    val language: String = DEFAULT_LANGUAGE,
    val renderedPrompt: String? = null,
    val promptVersion: String? = null,
    val model: String? = null,
    val rawResponse: String? = null,
    val result: PipelineResult? = null,
    val startedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}

/**
 * In-memory carrier for per-session pipeline state, keyed by session ID.
 *
 * Pipeline commands carry only a session ID, so stages have no other way to hand
 * work to each other. This store fills that gap: each stage reads what it needs by
 * session ID and writes what it produces.
 *
 * State lives in memory only and does not survive process death. WorkManager can
 * restart a run after the process is killed, and such a run will find its state
 * missing — [requireTranscript] and friends then fail with
 * [MissingSessionStateException] rather than letting the run continue on empty
 * values. Persisting this state is the change needed to make mid-run recovery work
 * end to end.
 */
@Singleton
class PipelineSessionStore @Inject constructor() {

    private val states = ConcurrentHashMap<String, PipelineSessionState>()

    /**
     * Creates the initial state for a run, replacing any state already held for
     * [sessionId].
     *
     * @param sessionId Session being started.
     * @param transcript Conversation text to analyse.
     * @param language Language of [transcript].
     */
    fun start(sessionId: String, transcript: String, language: String) {
        states[sessionId] = PipelineSessionState(
            transcript = transcript,
            language = language
        )
    }

    /**
     * Returns the state for [sessionId], or `null` if the session is unknown.
     */
    fun get(sessionId: String): PipelineSessionState? = states[sessionId]

    /**
     * Applies [transform] to the state for [sessionId], creating an empty state first
     * if none exists.
     *
     * @return The updated state.
     */
    fun update(
        sessionId: String,
        transform: (PipelineSessionState) -> PipelineSessionState
    ): PipelineSessionState = states.compute(sessionId) { _, existing ->
        transform(existing ?: PipelineSessionState())
    }!!

    /** Discards state for [sessionId]. Call once a run reaches a terminal stage. */
    fun clear(sessionId: String) {
        states.remove(sessionId)
    }

    /**
     * @return The transcript for [sessionId].
     * @throws MissingSessionStateException if absent or blank.
     */
    fun requireTranscript(sessionId: String): String =
        require(sessionId, "transcript") { it.transcript }

    /**
     * @return The rendered prompt for [sessionId].
     * @throws MissingSessionStateException if absent or blank.
     */
    fun requirePrompt(sessionId: String): String =
        require(sessionId, "renderedPrompt") { it.renderedPrompt }

    /**
     * @return The raw model output for [sessionId].
     * @throws MissingSessionStateException if absent or blank.
     */
    fun requireRawResponse(sessionId: String): String =
        require(sessionId, "rawResponse") { it.rawResponse }

    /**
     * @return The parsed result for [sessionId].
     * @throws MissingSessionStateException if absent.
     */
    fun requireResult(sessionId: String): PipelineResult =
        states[sessionId]?.result
            ?: throw MissingSessionStateException(sessionId, "result")

    private fun require(
        sessionId: String,
        field: String,
        select: (PipelineSessionState) -> String?
    ): String {
        val value = states[sessionId]?.let(select)
        if (value.isNullOrBlank()) {
            throw MissingSessionStateException(sessionId, field)
        }
        return value
    }
}
