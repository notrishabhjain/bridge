package com.sentinel.bridge.analyze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sentinel.bridge.core.domain.model.PipelineResult
import dagger.hilt.android.AndroidEntryPoint

/**
 * Screen for analysing a conversation pasted in by hand.
 *
 * The stages that drive the Recorder UI to obtain a transcript are not yet
 * implemented. This screen supplies a transcript directly so the rest of the pipeline
 * — prompt, on-device inference, parsing, persistence, and the MacroDroid broadcast —
 * can be exercised and verified end to end.
 */
@AndroidEntryPoint
class AnalyzeTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnalyzeScreen()
                }
            }
        }
    }
}

@Composable
private fun AnalyzeScreen(viewModel: AnalyzeTextViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var transcript by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("English") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Analyse a conversation",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Paste a call transcript or any conversation. It is analysed on this " +
                "device and the extracted tasks are broadcast to MacroDroid.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = transcript,
            onValueChange = { transcript = it },
            label = { Text("Transcript") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            enabled = uiState !is AnalyzeUiState.Running
        )

        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Language") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = uiState !is AnalyzeUiState.Running
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.analyze(transcript, language) },
                enabled = transcript.isNotBlank() && uiState !is AnalyzeUiState.Running
            ) {
                Text("Analyse")
            }
            if (uiState !is AnalyzeUiState.Idle && uiState !is AnalyzeUiState.Running) {
                OutlinedButton(onClick = viewModel::reset) {
                    Text("Clear result")
                }
            }
        }

        when (val state = uiState) {
            is AnalyzeUiState.Idle -> Unit

            is AnalyzeUiState.Running -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = "Running on-device inference. A few minutes is normal on " +
                            "phone hardware; it gives up after five.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(onClick = viewModel::cancel) {
                    Text("Stop")
                }
            }

            is AnalyzeUiState.Error -> Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Analysis failed", style = MaterialTheme.typography.titleSmall)
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
            }

            is AnalyzeUiState.Success -> ResultView(state.result)
        }
    }
}

@Composable
private fun ResultView(result: PipelineResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Summary", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = result.summary.ifBlank { "(none)" },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Confidence ${result.confidence} · " +
                        "${result.processingTimeMs} ms · ${result.model}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Text(
            text = "Tasks (${result.tasks.size})",
            style = MaterialTheme.typography.titleSmall
        )
        if (result.tasks.isEmpty()) {
            Text("No tasks found.", style = MaterialTheme.typography.bodySmall)
        }
        result.tasks.forEach { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    if (task.description.isNotBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "${task.priority}" +
                            (task.dueDate?.let { " · due $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Text(
            text = "Calendar events (${result.calendarEvents.size})",
            style = MaterialTheme.typography.titleSmall
        )
        if (result.calendarEvents.isEmpty()) {
            Text("No events found.", style = MaterialTheme.typography.bodySmall)
        }
        result.calendarEvents.forEach { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(event.title, style = MaterialTheme.typography.bodyLarge)
                    Text(event.dateTime, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (result.people.isNotEmpty()) {
            Text(
                text = "People: ${result.people.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
