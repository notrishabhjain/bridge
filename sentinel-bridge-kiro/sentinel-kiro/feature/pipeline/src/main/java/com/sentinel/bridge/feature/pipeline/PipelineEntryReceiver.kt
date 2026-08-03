package com.sentinel.bridge.feature.pipeline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinel.bridge.core.common.logging.SentinelLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Entry point for the Sentinel pipeline, triggered by MacroDroid via an explicit broadcast.
 *
 * This [BroadcastReceiver] listens for the [ACTION_START_PIPELINE] intent action. When received,
 * it validates the required extras (callTimestamp, callerName, phoneNumber, callDuration,
 * macroInvocationId), generates a UUID-based session identifier, and delegates pipeline
 * execution to [PipelineOrchestrator].
 *
 * ## Lifecycle
 * Since [PipelineOrchestrator.startPipeline] is a suspend function (Room insert + WorkManager
 * enqueue), this receiver uses [goAsync] to extend its lifecycle beyond the default 10-second
 * broadcast timeout. A [SupervisorJob]-backed [CoroutineScope] tied to [Dispatchers.IO] executes
 * the orchestrator call, and [PendingResult.finish] is called on completion.
 *
 * ## Validation
 * If the intent action does not match [ACTION_START_PIPELINE], or if required extras are missing,
 * the receiver logs a structured error via [SentinelLogger] and returns without starting the pipeline.
 *
 * ## Hilt Integration
 * Annotated with [AndroidEntryPoint] for constructor injection of [PipelineOrchestrator] and
 * [SentinelLogger].
 *
 * @see PipelineOrchestrator.startPipeline
 * @see PipelineWorker
 */
@AndroidEntryPoint
class PipelineEntryReceiver : BroadcastReceiver() {

    @Inject
    lateinit var orchestrator: PipelineOrchestrator

    @Inject
    lateinit var logger: SentinelLogger

    companion object {
        /** Intent action that MacroDroid sends to trigger the pipeline. */
        const val ACTION_START_PIPELINE = "com.sentinel.bridge.START_PIPELINE"

        /** Extra key: ISO-8601 timestamp of the call event. */
        const val EXTRA_CALL_TIMESTAMP = "callTimestamp"

        /** Extra key: display name of the caller (nullable in practice, required in contract). */
        const val EXTRA_CALLER_NAME = "callerName"

        /** Extra key: phone number of the caller. */
        const val EXTRA_PHONE_NUMBER = "phoneNumber"

        /** Extra key: call duration in seconds. */
        const val EXTRA_CALL_DURATION = "callDuration"

        /** Extra key: MacroDroid invocation identifier for result correlation. */
        const val EXTRA_MACRO_INVOCATION_ID = "macroInvocationId"

        private const val STAGE = "PIPELINE_ENTRY_RECEIVER"
        private const val SOURCE = "CALL"
        private const val DEFAULT_LANGUAGE = "Hindi"
    }

    /**
     * Handles the incoming broadcast intent.
     *
     * Validates the action matches [ACTION_START_PIPELINE] and that all required extras are
     * present. On validation success, generates a UUID session identifier, obtains a
     * [PendingResult] via [goAsync], and launches a coroutine to invoke
     * [PipelineOrchestrator.startPipeline]. Calls [PendingResult.finish] when complete.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received, expected to carry [ACTION_START_PIPELINE].
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_PIPELINE) {
            logger.logError(
                sessionId = "unknown",
                stage = STAGE,
                message = "Received intent with unexpected action: ${intent.action}"
            )
            return
        }

        val callTimestamp = intent.getStringExtra(EXTRA_CALL_TIMESTAMP)
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        val callDuration = if (intent.hasExtra(EXTRA_CALL_DURATION)) {
            intent.getLongExtra(EXTRA_CALL_DURATION, 0L)
        } else {
            null
        }
        val macroInvocationId = intent.getStringExtra(EXTRA_MACRO_INVOCATION_ID)

        val missingExtras = buildList {
            if (callTimestamp == null) add(EXTRA_CALL_TIMESTAMP)
            if (callerName == null) add(EXTRA_CALLER_NAME)
            if (phoneNumber == null) add(EXTRA_PHONE_NUMBER)
            if (callDuration == null) add(EXTRA_CALL_DURATION)
            if (macroInvocationId == null) add(EXTRA_MACRO_INVOCATION_ID)
        }

        if (missingExtras.isNotEmpty()) {
            logger.logError(
                sessionId = "unknown",
                stage = STAGE,
                message = "Missing required extras: ${missingExtras.joinToString()}"
            )
            return
        }

        val sessionId = UUID.randomUUID().toString()

        logger.logInfo(
            sessionId = sessionId,
            stage = STAGE,
            message = "Received START_PIPELINE intent, caller=$callerName, phone=$phoneNumber"
        )

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                val started = orchestrator.startPipeline(
                    sessionId = sessionId,
                    source = SOURCE,
                    language = DEFAULT_LANGUAGE,
                    callerName = callerName,
                    phoneNumber = phoneNumber,
                    callDuration = callDuration,
                    macroInvocationId = macroInvocationId
                )

                if (started) {
                    logger.logInfo(
                        sessionId = sessionId,
                        stage = STAGE,
                        message = "Pipeline enqueued successfully"
                    )
                } else {
                    logger.logError(
                        sessionId = sessionId,
                        stage = STAGE,
                        message = "Pipeline start rejected (capability check failed)"
                    )
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.logError(
                    sessionId = sessionId,
                    stage = STAGE,
                    message = "Failed to start pipeline: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
