package com.sentinel.bridge.feature.pipeline

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import com.sentinel.bridge.core.data.repository.LogRepository
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.setup.CapabilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for the Sentinel pipeline state machine.
 *
 * Coordinates pre-flight capability checks, session creation, WorkManager enqueuing,
 * and sequential command execution via the [CommandBus]. Each pipeline stage transition
 * is persisted to Room before execution, enabling resume-from-crash semantics via
 * [WorkManager].
 *
 * The orchestrator enforces a single-pipeline-at-a-time policy through
 * [ExistingWorkPolicy.KEEP] — if a pipeline is already running, subsequent triggers
 * are silently dropped by WorkManager.
 *
 * ## Lifecycle
 * 1. [startPipeline] — validates capabilities, creates session, enqueues work.
 * 2. [PipelineWorker] picks up the work, calls [resumePipeline].
 * 3. [resumePipeline] — loads session, resolves remaining commands, iterates via [CommandBus].
 *
 * @property context Application context for WorkManager access.
 * @property capabilityManager Pre-flight device readiness checker.
 * @property commandBus Central dispatch bus for pipeline commands.
 * @property pipelineSessionDao DAO for direct stage-level session updates.
 * @property logRepository Repository for session insert with rotation.
 * @property appSettingsRepository DataStore-backed settings (e.g. transcription timeout).
 * @property sessionStore Carrier for per-run state handed between stages.
 * @property logger Structured logger for pipeline events.
 */
@Singleton
class PipelineOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val commandBus: CommandBus,
    private val pipelineSessionDao: PipelineSessionDao,
    private val logRepository: LogRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val sessionStore: PipelineSessionStore,
    private val logger: SentinelLogger
) {

    companion object {
        /** Source recorded for runs started from pasted text rather than a call. */
        const val MANUAL_SOURCE = "MANUAL"

        /** Unique work name for WorkManager. Only one pipeline runs at a time. */
        const val WORK_NAME = "sentinel_pipeline"

        /** Input data key for passing sessionId to PipelineWorker. */
        const val KEY_SESSION_ID = "session_id"
    }

    /**
     * Initiates a new pipeline execution.
     *
     * Performs a pre-flight capability check via [CapabilityManager.checkAllCapabilities].
     * If any capability is unavailable, logs the failure and returns `false` without
     * enqueuing work. Otherwise, creates a [PipelineSessionEntity] at stage
     * [PipelineStage.PIPELINE_CREATED], persists it with rotation enforcement, and
     * enqueues a [OneTimeWorkRequest] for [PipelineWorker].
     *
     * @param sessionId Unique identifier for this pipeline session (UUID).
     * @param source The event source name that triggered this pipeline (e.g., "CALL").
     * @param language Transcription language code (e.g., "Hindi").
     * @param callerName Display name of the caller, or null if unavailable.
     * @param phoneNumber Phone number of the caller, or null if unavailable.
     * @param callDuration Duration of the call in seconds, or null if not applicable.
     * @param macroInvocationId MacroDroid invocation identifier for result correlation.
     * @return `true` if the pipeline was successfully enqueued; `false` if capabilities failed.
     */
    suspend fun startPipeline(
        sessionId: String,
        source: String,
        language: String,
        callerName: String?,
        phoneNumber: String?,
        callDuration: Long?,
        macroInvocationId: String?
    ): Boolean {
        val report = capabilityManager.checkAllCapabilities()

        if (!report.allPassed) {
            logger.logError(
                sessionId = sessionId,
                stage = PipelineStage.CAPABILITY_CHECK.name,
                message = "Capability check failed: accessibility=${report.accessibilityEnabled}, " +
                    "notificationListener=${report.notificationListenerEnabled}, " +
                    "recorder=${report.recorderInstalled}, model=${report.modelValid}, " +
                    "ram=${report.sufficientRam}, storage=${report.sufficientStorage}"
            )
            return false
        }

        val now = Instant.now().toEpochMilli()
        val session = PipelineSessionEntity(
            sessionId = sessionId,
            source = source,
            currentStage = PipelineStage.PIPELINE_CREATED.name,
            language = language,
            callerName = callerName,
            phoneNumber = phoneNumber,
            callDuration = callDuration,
            macroInvocationId = macroInvocationId,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            errorCode = null,
            errorCategory = null,
            errorMessage = null,
            retryCount = 0
        )

        logRepository.insertWithRotation(session)

        logger.logInfo(
            sessionId = sessionId,
            stage = PipelineStage.PIPELINE_CREATED.name,
            message = "Pipeline session created, enqueuing work"
        )

        val workRequest = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)

        return true
    }

    /**
     * Runs the analysis half of the pipeline over text supplied directly, skipping the
     * recorder-automation stages.
     *
     * The stages that drive the Recorder UI exist to obtain a transcript. When the
     * caller already has one, those stages have nothing to do, so the run is seeded at
     * [PipelineStage.BUILD_PROMPT] and everything from prompt building through dispatch
     * executes normally.
     *
     * Capability requirements differ accordingly: the model must be present and the
     * device must have headroom to run it, but accessibility and the Recorder are not
     * consulted, since nothing here touches them.
     *
     * Unlike [startPipeline] this executes inline and returns the outcome, rather than
     * enqueuing work — the caller is a person waiting on a screen for the result.
     *
     * @param transcript Conversation text to analyse.
     * @param language Language of [transcript], used when rendering the prompt.
     * @return The terminal [CommandResult] of the run.
     */
    suspend fun analyzeText(transcript: String, language: String): CommandResult {
        val sessionId = UUID.randomUUID().toString()

        if (transcript.isBlank()) {
            return CommandResult.Failure(
                SentinelError(
                    code = "ERR_EMPTY_TRANSCRIPT",
                    category = ErrorCategory.SYSTEM,
                    message = "There is no text to analyse.",
                    stage = PipelineStage.BUILD_PROMPT,
                    retryable = false,
                    timestamp = Instant.now(),
                    sessionId = sessionId
                )
            )
        }

        val report = capabilityManager.checkAllCapabilities()
        if (!report.modelValid || !report.sufficientRam || !report.sufficientStorage) {
            logger.logError(
                sessionId = sessionId,
                stage = PipelineStage.CAPABILITY_CHECK.name,
                message = "Manual analysis blocked: model=${report.modelValid}, " +
                    "ram=${report.sufficientRam} (${report.availableRamMb}/${report.totalRamMb} MB " +
                    "free, lowMemory=${report.lowMemory}), " +
                    "storage=${report.sufficientStorage} (${report.availableStorageMb} MB free)"
            )
            return CommandResult.Failure(
                SentinelError(
                    code = "ERR_CAPABILITY_CHECK",
                    category = ErrorCategory.SYSTEM,
                    // Quote the measured figures. "Not enough memory" alone gives the
                    // user nothing to act on and nothing to report back.
                    message = buildString {
                        append("Cannot run the model: ")
                        val problems = buildList {
                            if (!report.modelValid) add("the model file is missing")
                            if (!report.sufficientRam) {
                                add(
                                    if (report.lowMemory) {
                                        "the system is low on memory right now " +
                                            "(${report.availableRamMb} MB free of " +
                                            "${report.totalRamMb} MB) — close some apps and retry"
                                    } else {
                                        "only ${report.availableRamMb} MB of " +
                                            "${report.totalRamMb} MB RAM is free"
                                    }
                                )
                            }
                            if (!report.sufficientStorage) {
                                add("only ${report.availableStorageMb} MB of storage is free")
                            }
                        }
                        append(problems.joinToString("; "))
                    },
                    stage = PipelineStage.CAPABILITY_CHECK,
                    retryable = true,
                    timestamp = Instant.now(),
                    sessionId = sessionId
                )
            )
        }

        val now = Instant.now().toEpochMilli()
        logRepository.insertWithRotation(
            PipelineSessionEntity(
                sessionId = sessionId,
                source = MANUAL_SOURCE,
                currentStage = PipelineStage.BUILD_PROMPT.name,
                language = language,
                callerName = null,
                phoneNumber = null,
                callDuration = null,
                macroInvocationId = null,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                errorCode = null,
                errorCategory = null,
                errorMessage = null,
                retryCount = 0
            )
        )

        sessionStore.start(sessionId, transcript, language)

        logger.logInfo(
            sessionId = sessionId,
            stage = PipelineStage.BUILD_PROMPT.name,
            message = "Manual analysis started (${transcript.length} chars, language=$language)"
        )

        return try {
            resumePipeline(sessionId)
        } finally {
            // resumePipeline only clears state on the happy path via RETURN_INTENT;
            // a failure part-way through would otherwise strand it in memory.
            sessionStore.clear(sessionId)
        }
    }

    /**
     * Resumes pipeline execution from the current persisted stage.
     *
     * Called by [PipelineWorker] after WorkManager dispatches the work. Loads the
     * session from Room, reads the transcription timeout from settings, builds the
     * remaining command list from the current stage onward, and iterates through
     * each command sequentially.
     *
     * For each command:
     * 1. Updates the session stage in Room (pre-persist for crash safety).
     * 2. Dispatches the command via [CommandBus].
     * 3. On [CommandResult.Success]: advances to the next command.
     * 4. On [CommandResult.Failure]: updates session with error info and returns failure.
     * 5. On [CommandResult.Skipped]: marks session as completed and returns skipped.
     *
     * After all commands succeed, marks the session as [PipelineStage.COMPLETE].
     *
     * @param sessionId The UUID of the session to resume.
     * @return The final [CommandResult] indicating pipeline outcome.
     */
    suspend fun resumePipeline(sessionId: String): CommandResult {
        val session = pipelineSessionDao.getById(sessionId)
            ?: return CommandResult.Failure(
                SentinelError(
                    code = "ERR_SESSION_NOT_FOUND",
                    category = ErrorCategory.SYSTEM,
                    message = "Pipeline session not found: $sessionId",
                    stage = PipelineStage.IDLE,
                    retryable = false,
                    timestamp = Instant.now(),
                    sessionId = sessionId
                )
            )

        val language = session.language
        val timeoutMs = appSettingsRepository.transcriptionTimeoutMs.first()

        val currentStage = PipelineStage.valueOf(session.currentStage)
        val commands = buildCommandList(sessionId, language, timeoutMs, currentStage)

        for (command in commands) {
            val stage = mapCommandToStage(command)
            val now = Instant.now().toEpochMilli()

            pipelineSessionDao.updateStage(sessionId, stage.name, now)

            logger.logInfo(
                sessionId = sessionId,
                stage = stage.name,
                message = "Executing stage"
            )

            val result = commandBus.dispatch(command)

            when (result) {
                is CommandResult.Success -> {
                    logger.logInfo(
                        sessionId = sessionId,
                        stage = stage.name,
                        message = "Stage completed successfully"
                    )
                }
                is CommandResult.Failure -> {
                    val error = result.error
                    val failedAt = Instant.now().toEpochMilli()
                    pipelineSessionDao.updateCompletion(
                        sessionId = sessionId,
                        stage = stage.name,
                        updatedAt = failedAt,
                        completedAt = failedAt,
                        errorCode = error.code,
                        errorCategory = error.category.name,
                        errorMessage = error.message
                    )
                    logger.logError(
                        sessionId = sessionId,
                        stage = stage.name,
                        message = "Pipeline failed: ${error.code} — ${error.message}"
                    )
                    return result
                }
                is CommandResult.Skipped -> {
                    val skippedAt = Instant.now().toEpochMilli()
                    pipelineSessionDao.updateCompletion(
                        sessionId = sessionId,
                        stage = PipelineStage.COMPLETE.name,
                        updatedAt = skippedAt,
                        completedAt = skippedAt,
                        errorCode = null,
                        errorCategory = null,
                        errorMessage = null
                    )
                    logger.logInfo(
                        sessionId = sessionId,
                        stage = stage.name,
                        message = "Pipeline skipped: ${result.reason}"
                    )
                    return result
                }
            }
        }

        val completedAt = Instant.now().toEpochMilli()
        pipelineSessionDao.updateCompletion(
            sessionId = sessionId,
            stage = PipelineStage.COMPLETE.name,
            updatedAt = completedAt,
            completedAt = completedAt,
            errorCode = null,
            errorCategory = null,
            errorMessage = null
        )

        logger.logInfo(
            sessionId = sessionId,
            stage = PipelineStage.COMPLETE.name,
            message = "Pipeline completed successfully"
        )

        return CommandResult.Success(sessionId)
    }

    /**
     * Builds the ordered list of [PipelineCommand]s from the given stage onward.
     *
     * The full pipeline command sequence starts at [PipelineStage.OPEN_RECORDER].
     * If the session is resuming from a later stage, only commands from that stage
     * onward are returned.
     *
     * @param sessionId The pipeline session identifier.
     * @param language The transcription language for the [PipelineCommand.SelectLanguage] command.
     * @param timeoutMs The transcription timeout for the [PipelineCommand.WaitForTranscription] command.
     * @param fromStage The stage from which to start building commands (inclusive).
     * @return Ordered list of [PipelineCommand] instances to execute.
     */
    private fun buildCommandList(
        sessionId: String,
        language: String,
        timeoutMs: Long,
        fromStage: PipelineStage
    ): List<PipelineCommand> {
        // Terminal or pre-execution stages have no commands to run
        if (fromStage == PipelineStage.COMPLETE) {
            return emptyList()
        }

        val allCommands = listOf(
            PipelineCommand.OpenRecorder(sessionId),
            PipelineCommand.OpenRecording(sessionId),
            PipelineCommand.ClickShowText(sessionId),
            PipelineCommand.SelectLanguage(sessionId, language),
            PipelineCommand.WaitForTranscription(sessionId, timeoutMs),
            PipelineCommand.ExtractTranscript(sessionId),
            PipelineCommand.RunPreprocessor(sessionId),
            PipelineCommand.RunRulesPreAI(sessionId),
            PipelineCommand.BuildPrompt(sessionId),
            PipelineCommand.RunInference(sessionId),
            PipelineCommand.ParseResponse(sessionId),
            PipelineCommand.ValidateJson(sessionId),
            PipelineCommand.RunRulesPostAI(sessionId),
            PipelineCommand.StoreResult(sessionId),
            PipelineCommand.DispatchAction(sessionId),
            PipelineCommand.ReturnIntent(sessionId)
        )

        val allStages = listOf(
            PipelineStage.OPEN_RECORDER,
            PipelineStage.OPEN_RECORDING,
            PipelineStage.CLICK_SHOW_TEXT,
            PipelineStage.SELECT_LANGUAGE,
            PipelineStage.WAIT_TRANSCRIPTION,
            PipelineStage.EXTRACT_TRANSCRIPT,
            PipelineStage.PREPROCESS,
            PipelineStage.RULES_PRE,
            PipelineStage.BUILD_PROMPT,
            PipelineStage.INFERENCE,
            PipelineStage.PARSE_RESPONSE,
            PipelineStage.VALIDATE_JSON,
            PipelineStage.RULES_POST,
            PipelineStage.STORE_RESULT,
            PipelineStage.DISPATCH_ACTION,
            PipelineStage.RETURN_INTENT
        )

        val startIndex = allStages.indexOf(fromStage).coerceAtLeast(0)
        return allCommands.subList(startIndex, allCommands.size)
    }

    /**
     * Maps a [PipelineCommand] to its corresponding [PipelineStage].
     *
     * Used to persist the current stage to Room before each command execution,
     * enabling WorkManager to resume from the correct point after process death.
     *
     * @param command The pipeline command to map.
     * @return The [PipelineStage] corresponding to the command.
     */
    private fun mapCommandToStage(command: PipelineCommand): PipelineStage {
        return when (command) {
            is PipelineCommand.OpenRecorder -> PipelineStage.OPEN_RECORDER
            is PipelineCommand.OpenRecording -> PipelineStage.OPEN_RECORDING
            is PipelineCommand.ClickShowText -> PipelineStage.CLICK_SHOW_TEXT
            is PipelineCommand.SelectLanguage -> PipelineStage.SELECT_LANGUAGE
            is PipelineCommand.WaitForTranscription -> PipelineStage.WAIT_TRANSCRIPTION
            is PipelineCommand.ExtractTranscript -> PipelineStage.EXTRACT_TRANSCRIPT
            is PipelineCommand.RunPreprocessor -> PipelineStage.PREPROCESS
            is PipelineCommand.RunRulesPreAI -> PipelineStage.RULES_PRE
            is PipelineCommand.BuildPrompt -> PipelineStage.BUILD_PROMPT
            is PipelineCommand.RunInference -> PipelineStage.INFERENCE
            is PipelineCommand.ParseResponse -> PipelineStage.PARSE_RESPONSE
            is PipelineCommand.ValidateJson -> PipelineStage.VALIDATE_JSON
            is PipelineCommand.RunRulesPostAI -> PipelineStage.RULES_POST
            is PipelineCommand.StoreResult -> PipelineStage.STORE_RESULT
            is PipelineCommand.DispatchAction -> PipelineStage.DISPATCH_ACTION
            is PipelineCommand.ReturnIntent -> PipelineStage.RETURN_INTENT
        }
    }
}
