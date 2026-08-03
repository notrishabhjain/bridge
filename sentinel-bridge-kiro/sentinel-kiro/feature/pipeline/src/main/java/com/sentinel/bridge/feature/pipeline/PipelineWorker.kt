package com.sentinel.bridge.feature.pipeline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sentinel.bridge.core.common.logging.SentinelLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager [CoroutineWorker] responsible for executing the Sentinel pipeline.
 *
 * This worker is enqueued by [PipelineOrchestrator.startPipeline] with a unique work
 * policy of [androidx.work.ExistingWorkPolicy.KEEP], ensuring only one pipeline runs
 * at a time. On execution, it extracts the `sessionId` from input data and delegates
 * to [PipelineOrchestrator.resumePipeline] which drives the pipeline state machine
 * from the last persisted stage.
 *
 * ## Resume-from-crash semantics
 * If the process dies mid-pipeline, WorkManager will re-dispatch this worker. The
 * orchestrator loads the session from Room and resumes from `currentStage`, making
 * every stage transition idempotent.
 *
 * ## Result mapping
 * - [CommandResult.Success] → [Result.success] — pipeline completed all stages.
 * - [CommandResult.Failure] → [Result.failure] with `errorCode` in output data.
 * - [CommandResult.Skipped] → [Result.success] — graceful early termination (e.g., pre-AI rule IGNORE).
 *
 * @property orchestrator The central pipeline state machine coordinator.
 * @property logger Structured logger for worker lifecycle events.
 */
@HiltWorker
class PipelineWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: PipelineOrchestrator,
    private val logger: SentinelLogger
) : CoroutineWorker(appContext, params) {

    /**
     * Executes the pipeline for the session identified in [inputData].
     *
     * Extracts the session identifier via [PipelineOrchestrator.KEY_SESSION_ID].
     * If the key is missing, logs the error and returns [Result.failure] immediately.
     * Otherwise delegates to [PipelineOrchestrator.resumePipeline] and maps the
     * [CommandResult] to an appropriate WorkManager [Result].
     *
     * @return [Result.success] on pipeline completion or graceful skip,
     *         [Result.failure] on missing session ID or pipeline error.
     */
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(PipelineOrchestrator.KEY_SESSION_ID)

        if (sessionId == null) {
            logger.logError(
                sessionId = "unknown",
                stage = "PIPELINE_WORKER",
                message = "Worker started without sessionId in input data"
            )
            return Result.failure()
        }

        logger.logInfo(
            sessionId = sessionId,
            stage = "PIPELINE_WORKER",
            message = "Worker starting pipeline execution"
        )

        return when (val result = orchestrator.resumePipeline(sessionId)) {
            is CommandResult.Success -> {
                logger.logInfo(
                    sessionId = sessionId,
                    stage = "PIPELINE_WORKER",
                    message = "Pipeline completed successfully"
                )
                Result.success()
            }
            is CommandResult.Failure -> {
                logger.logError(
                    sessionId = sessionId,
                    stage = "PIPELINE_WORKER",
                    message = "Pipeline failed: ${result.error.code}"
                )
                Result.failure(workDataOf("errorCode" to result.error.code))
            }
            is CommandResult.Skipped -> {
                logger.logInfo(
                    sessionId = sessionId,
                    stage = "PIPELINE_WORKER",
                    message = "Pipeline skipped: ${result.reason}"
                )
                Result.success()
            }
        }
    }
}
