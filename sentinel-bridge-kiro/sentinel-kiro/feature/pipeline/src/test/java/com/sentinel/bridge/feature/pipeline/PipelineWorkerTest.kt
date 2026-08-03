package com.sentinel.bridge.feature.pipeline

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [PipelineWorker] verifying the doWork() result mapping:
 * - Valid sessionId + orchestrator Success → Result.success()
 * - Missing sessionId → Result.failure()
 * - Orchestrator Failure → Result.failure(workDataOf("errorCode" to ...))
 * - Orchestrator Skipped → Result.success()
 *
 * Since [PipelineWorker] is a CoroutineWorker annotated with @HiltWorker,
 * direct instantiation requires mocking the Android Context and WorkerParameters.
 * We construct the worker directly with mocked dependencies to validate result mapping.
 */
class PipelineWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var orchestrator: PipelineOrchestrator
    private lateinit var logger: SentinelLogger

    private val testSessionId = "worker-test-session-001"

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        orchestrator = mockk()
        logger = mockk(relaxed = true)

        // Default: no tags, no network constraints
        every { workerParams.id } returns UUID.randomUUID()
        every { workerParams.tags } returns setOf("test")
        every { workerParams.runAttemptCount } returns 0
    }

    private fun createWorkerWithInput(inputData: Data): PipelineWorker {
        every { workerParams.inputData } returns inputData
        return PipelineWorker(context, workerParams, orchestrator, logger)
    }

    @Nested
    @DisplayName("doWork() result mapping")
    inner class DoWorkTests {

        @Test
        @DisplayName("Valid sessionId + orchestrator returns Success → Result.success()")
        fun `doWork returns success when orchestrator succeeds`() = runTest {
            val inputData = Data.Builder()
                .putString(PipelineOrchestrator.KEY_SESSION_ID, testSessionId)
                .build()

            val worker = createWorkerWithInput(inputData)
            coEvery { orchestrator.resumePipeline(testSessionId) } returns
                CommandResult.Success(testSessionId)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify { logger.logInfo(sessionId = testSessionId, stage = "PIPELINE_WORKER", message = any()) }
        }

        @Test
        @DisplayName("Missing sessionId in input data → Result.failure()")
        fun `doWork returns failure when sessionId is missing`() = runTest {
            val inputData = Data.Builder().build() // No sessionId

            val worker = createWorkerWithInput(inputData)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            verify {
                logger.logError(
                    sessionId = "unknown",
                    stage = "PIPELINE_WORKER",
                    message = any()
                )
            }
        }

        @Test
        @DisplayName("Orchestrator returns Failure → Result.failure(workDataOf(errorCode))")
        fun `doWork returns failure with errorCode when orchestrator fails`() = runTest {
            val inputData = Data.Builder()
                .putString(PipelineOrchestrator.KEY_SESSION_ID, testSessionId)
                .build()

            val error = SentinelError(
                code = "ERR_INFERENCE_TIMEOUT",
                category = ErrorCategory.INFERENCE,
                message = "LLM inference timed out after 60s",
                stage = PipelineStage.INFERENCE,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            val worker = createWorkerWithInput(inputData)
            coEvery { orchestrator.resumePipeline(testSessionId) } returns
                CommandResult.Failure(error)

            val result = worker.doWork()

            // Result.failure() with output data containing the error code
            assertTrue(result is ListenableWorker.Result)
            val outputData = result.outputData
            assertEquals("ERR_INFERENCE_TIMEOUT", outputData.getString("errorCode"))
            verify {
                logger.logError(
                    sessionId = testSessionId,
                    stage = "PIPELINE_WORKER",
                    message = any()
                )
            }
        }

        @Test
        @DisplayName("Orchestrator returns Skipped → Result.success()")
        fun `doWork returns success when orchestrator returns skipped`() = runTest {
            val inputData = Data.Builder()
                .putString(PipelineOrchestrator.KEY_SESSION_ID, testSessionId)
                .build()

            val worker = createWorkerWithInput(inputData)
            coEvery { orchestrator.resumePipeline(testSessionId) } returns
                CommandResult.Skipped(testSessionId, "Pre-AI rule returned IGNORE")

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify { logger.logInfo(sessionId = testSessionId, stage = "PIPELINE_WORKER", message = any()) }
        }
    }
}
