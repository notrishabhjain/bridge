package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central dispatch bus for pipeline commands.
 *
 * The [CommandBus] holds a [Channel] of [PipelineCommand] entries in a dedicated
 * [CoroutineScope]. Commands are dispatched exclusively through this bus — never
 * via direct calls, reflection, or EventBus.
 *
 * The bus serializes command execution through a single consumer coroutine, ensuring
 * only one pipeline stage runs at a time. Callers use [dispatch] to send a command
 * and suspend until the result is available (Channel + [CompletableDeferred] pattern).
 *
 * Handler resolution uses Hilt multibinding: each [PipelineCommand] subclass has
 * exactly one [CommandHandler] implementation bound in the DI graph. The handler map
 * is injected at construction time.
 *
 * @property handlers Map of command types to their handlers, provided by Hilt multibinding.
 */
@Singleton
class CommandBus @Inject constructor(
    private val handlers: Map<Class<out PipelineCommand>, @JvmSuppressWildcards CommandHandler<PipelineCommand>>
) {

    /**
     * Internal message type pairing a command with a deferred result slot.
     */
    private data class Envelope(
        val command: PipelineCommand,
        val deferred: CompletableDeferred<CommandResult>
    )

    /**
     * Unbounded channel that queues command envelopes for sequential processing.
     * Using [Channel.UNLIMITED] avoids back-pressure blocking the orchestrator
     * while the consumer processes the current command.
     */
    private val channel = Channel<Envelope>(Channel.UNLIMITED)

    /**
     * Dedicated scope for the command consumer coroutine.
     * Uses [SupervisorJob] so that a failure in one command does not cancel the bus.
     */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            for (envelope in channel) {
                val result = dispatchToHandler(envelope.command)
                envelope.deferred.complete(result)
            }
        }
    }

    /**
     * Dispatches a [PipelineCommand] through the channel and suspends until the
     * registered handler completes execution.
     *
     * The command is enqueued into the internal channel and processed by the
     * single consumer coroutine. The caller suspends on the [CompletableDeferred]
     * until the handler returns a [CommandResult].
     *
     * @param command The pipeline command to dispatch.
     * @return The [CommandResult] produced by the handler.
     */
    suspend fun dispatch(command: PipelineCommand): CommandResult {
        val deferred = CompletableDeferred<CommandResult>()
        channel.send(Envelope(command, deferred))
        return deferred.await()
    }

    /**
     * Resolves the correct handler for the command and invokes it.
     *
     * If no handler is registered for the command's type, returns a
     * [CommandResult.Failure] with error code `ERR_NO_HANDLER`.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatchToHandler(command: PipelineCommand): CommandResult {
        val handler = handlers[command::class.java]
            ?: return CommandResult.Failure(
                SentinelError(
                    code = "ERR_NO_HANDLER",
                    category = ErrorCategory.SYSTEM,
                    message = "No handler registered for ${command::class.simpleName}",
                    stage = PipelineStage.IDLE,
                    retryable = false,
                    timestamp = Instant.now(),
                    sessionId = ""
                )
            )
        return handler.execute(command)
    }

    /**
     * Closes the command channel and cancels the dedicated scope.
     *
     * After calling [close], no further commands can be dispatched.
     * Any pending commands in the channel will be discarded.
     */
    fun close() {
        channel.close()
        scope.cancel()
    }
}
