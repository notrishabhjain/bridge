package com.sentinel.bridge.analyze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.bridge.core.data.db.dao.PipelineResultDao
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineOrchestrator
import com.sentinel.bridge.feature.pipeline.PipelineResultJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the analyse screen is currently showing.
 */
sealed interface AnalyzeUiState {

    /** Waiting for text to be entered. */
    data object Idle : AnalyzeUiState

    /** Inference is running. On-device generation takes a while, so this is a real state. */
    data object Running : AnalyzeUiState

    /**
     * The run finished and produced a result.
     *
     * @property result Extracted tasks, events, and summary.
     * @property broadcast Whether the result was broadcast to MacroDroid.
     */
    data class Success(
        val result: PipelineResult,
        val broadcast: Boolean
    ) : AnalyzeUiState

    /**
     * The run stopped before producing a result.
     *
     * @property message What went wrong, in terms the user can act on.
     */
    data class Error(val message: String) : AnalyzeUiState
}

/**
 * Drives the paste-a-transcript screen.
 *
 * Runs the analysis half of the pipeline over text supplied by hand, so the model,
 * prompt, and MacroDroid handoff can be exercised without the recorder automation
 * stages, which are not yet implemented.
 */
@HiltViewModel
class AnalyzeTextViewModel @Inject constructor(
    private val orchestrator: PipelineOrchestrator,
    private val pipelineResultDao: PipelineResultDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)

    /** Current screen state. */
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    /**
     * Analyses [transcript] and publishes the outcome to [uiState].
     *
     * @param transcript Conversation text to analyse.
     * @param language Language of [transcript].
     */
    fun analyze(transcript: String, language: String) {
        if (_uiState.value is AnalyzeUiState.Running) return

        _uiState.value = AnalyzeUiState.Running

        viewModelScope.launch {
            _uiState.value = when (val outcome = orchestrator.analyzeText(transcript, language)) {
                is CommandResult.Success -> loadResult(outcome.sessionId)

                is CommandResult.Skipped -> AnalyzeUiState.Error(
                    "Analysis was skipped: ${outcome.reason}"
                )

                is CommandResult.Failure -> AnalyzeUiState.Error(
                    "${outcome.error.message} (${outcome.error.code}, " +
                        "stage ${outcome.error.stage.name})"
                )
            }
        }
    }

    /** Returns the screen to its initial state. */
    fun reset() {
        _uiState.value = AnalyzeUiState.Idle
    }

    /**
     * Reads back the persisted result for [sessionId].
     *
     * The run reports success once the store and dispatch stages have completed, so a
     * missing row here means persistence silently failed and is surfaced as an error
     * rather than an empty success.
     */
    private suspend fun loadResult(sessionId: String): AnalyzeUiState {
        val stored = pipelineResultDao.getBySessionId(sessionId)
            ?: return AnalyzeUiState.Error(
                "The run finished but no result was saved for session $sessionId."
            )

        return AnalyzeUiState.Success(
            result = PipelineResult(
                sessionId = stored.sessionId,
                summary = stored.summary,
                confidence = stored.confidence,
                tasks = PipelineResultJson.readTasks(stored.tasksJson),
                calendarEvents = PipelineResultJson.readCalendarEvents(stored.calendarEventsJson),
                followUps = PipelineResultJson.readFollowUps(stored.followUpsJson),
                people = PipelineResultJson.readStrings(stored.peopleJson),
                projects = PipelineResultJson.readStrings(stored.projectsJson),
                processingTimeMs = stored.processingTimeMs,
                model = stored.model,
                promptVersion = stored.promptVersion,
                pipelineVersion = stored.pipelineVersion
            ),
            broadcast = true
        )
    }
}
