package com.sentinel.bridge.feature.setup

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.datastore.FeatureFlagsRepository
import com.sentinel.bridge.feature.ai.provider.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel driving the Setup Wizard UI.
 *
 * Manages the sequential flow through [SetupStep] stages, checking each capability
 * in turn. Exposes reactive state for the current step, step statuses, and model
 * download progress. Coordinates with [CapabilityManager], [FeatureFlagsRepository],
 * [ModelRepository], and [AppSettingsRepository] to verify and record device readiness.
 *
 * The ViewModel uses Android [DownloadManager] for model download — the only network
 * operation in the entire application, gated behind explicit user action.
 *
 * @param context Application context for DownloadManager and file access.
 * @param capabilityManager Checks device capabilities (accessibility, notifications, etc.).
 * @param featureFlagsRepository Persists the `setupComplete` flag.
 * @param modelRepository Provides model configuration and checksum verification.
 * @param appSettingsRepository Provides device thresholds and recorder package config.
 */
@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val featureFlagsRepository: FeatureFlagsRepository,
    private val modelRepository: ModelRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(SetupStep.ACCESSIBILITY)

    /**
     * The current [SetupStep] being displayed or executed in the wizard.
     */
    val currentStep: StateFlow<SetupStep> = _currentStep.asStateFlow()

    private val _stepStatuses = MutableStateFlow(
        SetupStep.entries.associateWith { StepStatus.PENDING }.toMutableMap()
    )

    /**
     * Status map for each [SetupStep], keyed by step enum value.
     */
    val stepStatuses: StateFlow<Map<SetupStep, StepStatus>> = _stepStatuses.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)

    /**
     * Model download progress as a fraction between 0.0 and 1.0.
     * Only meaningful during the [SetupStep.MODEL_DOWNLOAD] step.
     */
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    /**
     * Human-readable error message for the current step, or `null` if no error.
     */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _setupComplete = MutableStateFlow(false)

    /**
     * Whether the setup wizard has completed all steps successfully.
     * The activity observes this to finish itself.
     */
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    private var downloadId: Long = -1L
    private var progressPollingJob: Job? = null

    /**
     * Executes the action for the current step.
     *
     * Each step checks its corresponding capability or initiates an operation.
     * On success, the wizard advances to the next step. On failure, the step
     * is marked [StepStatus.FAILED] and an error message is set.
     *
     * For [SetupStep.MODEL_DOWNLOAD], this initiates the download via [DownloadManager]
     * and begins progress polling. Completion is handled asynchronously.
     */
    fun executeCurrentStep() {
        val step = _currentStep.value
        updateStepStatus(step, StepStatus.IN_PROGRESS)
        _errorMessage.value = null

        viewModelScope.launch {
            when (step) {
                SetupStep.ACCESSIBILITY -> checkAccessibility()
                SetupStep.NOTIFICATION_LISTENER -> checkNotificationListener()
                SetupStep.DEVICE_CHECK -> checkDevice()
                SetupStep.RECORDER_INSPECTION -> inspectRecorder()
                SetupStep.MODEL_DOWNLOAD -> startModelDownload()
                SetupStep.CHECKSUM_VERIFY -> verifyChecksum()
                SetupStep.COMPLETE -> completeSetup()
            }
        }
    }

    /**
     * Checks whether the Sentinel Accessibility Service is enabled.
     * Delegates to [CapabilityManager.checkAllCapabilities].
     */
    private suspend fun checkAccessibility() {
        val report = capabilityManager.checkAllCapabilities()
        if (report.accessibilityEnabled) {
            markStepCompleteAndAdvance(SetupStep.ACCESSIBILITY)
        } else {
            markStepFailed(
                SetupStep.ACCESSIBILITY,
                "Accessibility service is not enabled. Please enable it in Settings."
            )
        }
    }

    /**
     * Checks whether the Sentinel Notification Listener is enabled.
     */
    private suspend fun checkNotificationListener() {
        val report = capabilityManager.checkAllCapabilities()
        if (report.notificationListenerEnabled) {
            markStepCompleteAndAdvance(SetupStep.NOTIFICATION_LISTENER)
        } else {
            markStepFailed(
                SetupStep.NOTIFICATION_LISTENER,
                "Notification listener is not enabled. Please enable it in Settings."
            )
        }
    }

    /**
     * Checks that the device is a supported Xiaomi HyperOS 2 device and that
     * the Recorder app is installed.
     */
    private suspend fun checkDevice() {
        val report = capabilityManager.checkAllCapabilities()
        if (!report.recorderInstalled) {
            markStepFailed(
                SetupStep.DEVICE_CHECK,
                "Xiaomi Recorder app is not installed on this device."
            )
            return
        }

        if (!report.sufficientRam) {
            markStepFailed(SetupStep.DEVICE_CHECK, "Insufficient RAM for model operation.")
            return
        }

        if (!report.sufficientStorage) {
            markStepFailed(SetupStep.DEVICE_CHECK, "Insufficient storage space.")
            return
        }

        markStepCompleteAndAdvance(SetupStep.DEVICE_CHECK)
    }

    /**
     * Launches the Recorder app via [CapabilityManager.recordCapabilityProfile] to
     * inspect and persist the UI structure as a [CapabilityProfileEntity].
     */
    private suspend fun inspectRecorder() {
        val profile = capabilityManager.recordCapabilityProfile()
        if (profile != null) {
            markStepCompleteAndAdvance(SetupStep.RECORDER_INSPECTION)
        } else {
            markStepFailed(
                SetupStep.RECORDER_INSPECTION,
                "Failed to inspect the Recorder UI. Ensure the accessibility service is active."
            )
        }
    }

    /**
     * Initiates model download using Android [DownloadManager].
     *
     * This is the ONLY place in the application where network is used, and it
     * requires explicit user action (tapping the download button in the wizard).
     * Progress is polled every 500ms and exposed via [downloadProgress].
     */
    private fun startModelDownload() {
        val config = modelRepository.loadConfig()
        val modelPath = modelRepository.getModelPath()
        val modelFile = File(modelPath)

        // If model already exists, skip download
        if (modelFile.exists()) {
            markStepCompleteAndAdvance(SetupStep.MODEL_DOWNLOAD)
            return
        }

        // Ensure parent directory exists
        modelFile.parentFile?.mkdirs()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(config.downloadUrl))
            .setTitle("Sentinel AI Model")
            .setDescription("Downloading ${config.name} model")
            .setDestinationUri(Uri.fromFile(modelFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        downloadId = downloadManager.enqueue(request)

        // Register completion receiver
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    progressPollingJob?.cancel()
                    _downloadProgress.value = 1f
                    handleDownloadComplete(downloadManager)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        // Poll progress
        progressPollingJob = viewModelScope.launch {
            while (isActive) {
                pollDownloadProgress(downloadManager)
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Polls [DownloadManager] for the current download progress and updates
     * [downloadProgress] as a fraction.
     */
    private fun pollDownloadProgress(downloadManager: DownloadManager) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor? = downloadManager.query(query)
        cursor?.use {
            if (it.moveToFirst()) {
                val bytesDownloaded = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val bytesTotal = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                if (bytesTotal > 0) {
                    _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                }
            }
        }
    }

    /**
     * Handles download completion by checking the [DownloadManager] status.
     * Advances to checksum verification on success, marks failed otherwise.
     */
    private fun handleDownloadComplete(downloadManager: DownloadManager) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor? = downloadManager.query(query)
        cursor?.use {
            if (it.moveToFirst()) {
                val status = it.getInt(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    markStepCompleteAndAdvance(SetupStep.MODEL_DOWNLOAD)
                } else {
                    val reason = it.getInt(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    )
                    markStepFailed(
                        SetupStep.MODEL_DOWNLOAD,
                        "Download failed (status=$status, reason=$reason). Please retry."
                    )
                }
            }
        }
    }

    /**
     * Verifies the SHA-256 checksum of the downloaded model file against the
     * expected value from `assets/model_config.json`.
     */
    private suspend fun verifyChecksum() {
        val valid = modelRepository.verifyChecksum()
        if (valid) {
            markStepCompleteAndAdvance(SetupStep.CHECKSUM_VERIFY)
        } else {
            markStepFailed(
                SetupStep.CHECKSUM_VERIFY,
                "Model checksum verification failed. The file may be corrupted."
            )
        }
    }

    /**
     * Completes the setup wizard by persisting the completion flag and capability profile.
     *
     * After this method:
     * - `featureFlagsRepository.setupComplete` = `true`
     * - A [CapabilityProfileEntity] is saved to Room (via [CapabilityManager.recordCapabilityProfile])
     * - [setupComplete] emits `true`, signaling the Activity to finish.
     */
    private suspend fun completeSetup() {
        featureFlagsRepository.setSetupComplete(true)
        // Record a final capability profile snapshot
        capabilityManager.recordCapabilityProfile()
        updateStepStatus(SetupStep.COMPLETE, StepStatus.COMPLETE)
        _setupComplete.value = true
    }

    /**
     * Marks a step as complete and advances to the next step in ordinal order.
     */
    private fun markStepCompleteAndAdvance(step: SetupStep) {
        updateStepStatus(step, StepStatus.COMPLETE)
        val nextOrdinal = step.ordinal + 1
        if (nextOrdinal < SetupStep.entries.size) {
            _currentStep.value = SetupStep.entries[nextOrdinal]
        }
    }

    /**
     * Marks a step as failed and sets the error message.
     */
    private fun markStepFailed(step: SetupStep, message: String) {
        updateStepStatus(step, StepStatus.FAILED)
        _errorMessage.value = message
    }

    /**
     * Updates the status of a single step in the status map.
     */
    private fun updateStepStatus(step: SetupStep, status: StepStatus) {
        _stepStatuses.value = _stepStatuses.value.toMutableMap().apply {
            this[step] = status
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressPollingJob?.cancel()
    }

    companion object {
        /** Interval in milliseconds between download progress polls. */
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
    }
}
