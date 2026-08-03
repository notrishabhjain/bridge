package com.sentinel.bridge.feature.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Setup Wizard Activity using Jetpack Compose UI.
 *
 * Guides the user through sequential setup steps required before the Sentinel AI Bridge
 * pipeline can operate. Steps are displayed in a list, each showing its title, description,
 * and current status. The active step provides an action button to initiate or retry the
 * capability check.
 *
 * The wizard finishes automatically when all steps pass and the [SetupWizardViewModel]
 * emits `setupComplete = true`.
 *
 * Steps in order:
 * 1. Accessibility Service permission
 * 2. Notification Listener permission
 * 3. Device Check (HyperOS 2 + Recorder installed)
 * 4. Recorder UI Inspection
 * 5. Model Download (only network use, behind explicit user action)
 * 6. Checksum Verification
 */
@AndroidEntryPoint
class SetupWizardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupWizardScreen(onSetupComplete = { finish() })
                }
            }
        }
    }
}

/**
 * Root composable for the setup wizard screen.
 *
 * Observes the [SetupWizardViewModel] state and renders step cards.
 * Automatically calls [onSetupComplete] when the wizard finishes.
 *
 * @param viewModel The Hilt-injected ViewModel managing wizard state.
 * @param onSetupComplete Callback invoked when setup completes successfully.
 */
@Composable
fun SetupWizardScreen(
    viewModel: SetupWizardViewModel = viewModel(),
    onSetupComplete: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val stepStatuses by viewModel.stepStatuses.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val setupComplete by viewModel.setupComplete.collectAsState()

    LaunchedEffect(setupComplete) {
        if (setupComplete) {
            onSetupComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Sentinel Setup",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Complete each step to configure Sentinel AI Bridge.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            val displaySteps = SetupStep.entries.filter { it != SetupStep.COMPLETE }
            items(displaySteps) { step ->
                SetupStepCard(
                    step = step,
                    status = stepStatuses[step] ?: StepStatus.PENDING,
                    isCurrentStep = step == currentStep,
                    downloadProgress = if (step == SetupStep.MODEL_DOWNLOAD) downloadProgress else null,
                    errorMessage = if (step == currentStep) errorMessage else null,
                    onAction = { viewModel.executeCurrentStep() }
                )
            }
        }
    }
}

/**
 * Card composable representing a single setup step.
 *
 * Displays the step title, description, status indicator, and an action button
 * when the step is active. Shows download progress for the model download step.
 *
 * @param step The [SetupStep] this card represents.
 * @param status Current [StepStatus] of the step.
 * @param isCurrentStep Whether this step is the one currently active.
 * @param downloadProgress Download progress fraction (0.0–1.0), or null if not applicable.
 * @param errorMessage Error text to display below the step, or null.
 * @param onAction Callback invoked when the action button is tapped.
 */
@Composable
fun SetupStepCard(
    step: SetupStep,
    status: StepStatus,
    isCurrentStep: Boolean,
    downloadProgress: Float?,
    errorMessage: String?,
    onAction: () -> Unit
) {
    val containerColor = when (status) {
        StepStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
        StepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
        StepStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                StepStatusIndicator(status = status)
            }

            // Download progress bar
            if (step == SetupStep.MODEL_DOWNLOAD && status == StepStatus.IN_PROGRESS && downloadProgress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Error message
            if (errorMessage != null && isCurrentStep) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Action button for current step
            if (isCurrentStep && status != StepStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val buttonText = when (status) {
                        StepStatus.FAILED -> "Retry"
                        StepStatus.PENDING -> "Check"
                        else -> "Continue"
                    }
                    Text(text = buttonText)
                }
            }
        }
    }
}

/**
 * Visual indicator for a step's current status.
 *
 * Renders a text label or spinning indicator depending on the [StepStatus].
 *
 * @param status The current status to display.
 */
@Composable
fun StepStatusIndicator(status: StepStatus) {
    when (status) {
        StepStatus.PENDING -> {
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepStatus.IN_PROGRESS -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
        StepStatus.COMPLETE -> {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        StepStatus.FAILED -> {
            Text(
                text = "\u2717",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
