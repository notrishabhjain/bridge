package com.sentinel.bridge.feature.setup

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.CapabilityProfileDao
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [CapabilityManager.checkAllCapabilities].
 *
 * Each test targets a single capability check branch, verifying that when a specific
 * capability is unavailable, the report reflects the failure and [CapabilityReport.allPassed]
 * is `false`. The "all pass" test confirms the happy path where every capability is available.
 *
 * Mocking approach:
 * - [Context]: MockK mock providing `contentResolver`, `packageManager`, `packageName`,
 *   `getExternalFilesDir()`, and `getSystemService()`.
 * - [AppSettingsRepository]: MockK mock using `flowOf(value)` for Flow properties.
 * - [CapabilityProfileDao]: MockK relaxed mock (not exercised by `checkAllCapabilities`).
 * - [Settings.Secure.getString]: Mocked via `mockkStatic` to control accessibility and
 *   notification listener settings.
 */
@DisplayName("CapabilityManager - checkAllCapabilities()")
class CapabilityManagerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var packageManager: PackageManager
    private lateinit var activityManager: ActivityManager
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var capabilityProfileDao: CapabilityProfileDao
    private lateinit var capabilityManager: CapabilityManager

    private lateinit var modelsDir: File
    private lateinit var externalDir: File

    @BeforeEach
    fun setUp() {
        mockkStatic(Settings.Secure::class)

        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        activityManager = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        capabilityProfileDao = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.sentinel.bridge"
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        // Default: AppSettings return safe thresholds
        every { appSettingsRepository.recorderPackage } returns flowOf("com.miui.voiceassist")
        every { appSettingsRepository.minFreeRamMb } returns flowOf(2048)
        every { appSettingsRepository.minFreeStorageMb } returns flowOf(500)

        // Default: models directory with a file present
        modelsDir = createTempDir("models").also { dir ->
            File(dir, "model.gguf").createNewFile()
        }
        every { context.getExternalFilesDir("models") } returns modelsDir

        // Default: external dir for storage check
        externalDir = createTempDir("external")
        every { context.getExternalFilesDir(null) } returns externalDir

        capabilityManager = CapabilityManager(context, appSettingsRepository, capabilityProfileDao)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Settings.Secure::class)
        modelsDir.deleteRecursively()
        externalDir.deleteRecursively()
    }

    /**
     * Helper to configure Settings.Secure for accessibility service enabled.
     */
    private fun stubAccessibilityEnabled(enabled: Boolean) {
        val componentString = if (enabled) {
            "com.sentinel.bridge/com.sentinel.bridge.feature.accessibility.SentinelAccessibilityService"
        } else {
            "com.other.app/com.other.app.SomeService"
        }
        every {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } returns componentString
    }

    /**
     * Helper to configure Settings.Secure for notification listener enabled.
     */
    private fun stubNotificationListenerEnabled(enabled: Boolean) {
        val componentString = if (enabled) {
            "com.sentinel.bridge/com.sentinel.bridge.feature.notification.SentinelNotificationListener"
        } else {
            "com.other.app/com.other.app.SomeListener"
        }
        every {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        } returns componentString
    }

    /**
     * Helper to configure PackageManager for recorder installed check.
     *
     * When [installed] is false every known recorder package must be stubbed to
     * throw, otherwise the fallback scan in `checkRecorderInstalled()` would
     * resolve one of the alternates against the relaxed mock.
     */
    @Suppress("DEPRECATION")
    private fun stubRecorderInstalled(installed: Boolean) {
        every {
            packageManager.getPackageInfo(any<String>(), 0)
        } throws PackageManager.NameNotFoundException()

        if (installed) {
            every { packageManager.getPackageInfo("com.miui.voiceassist", 0) } returns PackageInfo()
        }
    }

    /**
     * Helper to configure ActivityManager memory info for RAM check.
     */
    /**
     * Configures reported memory.
     *
     * The insufficient value sits clearly below the 1024 MB default rather than exactly
     * on it, so the test cannot be flipped by a boundary change.
     *
     * @param sufficient Whether free RAM should clear the threshold.
     * @param lowMemory Whether the system reports active memory pressure.
     */
    private fun stubSufficientRam(sufficient: Boolean, lowMemory: Boolean = false) {
        val availMb = if (sufficient) 4096L else 512L
        val availBytes = availMb * 1_048_576L

        every { activityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            // availMem, totalMem and lowMemory are public fields, set reflectively.
            ActivityManager.MemoryInfo::class.java.getField("availMem").set(memInfo, availBytes)
            ActivityManager.MemoryInfo::class.java.getField("totalMem")
                .set(memInfo, 8192L * 1_048_576L)
            ActivityManager.MemoryInfo::class.java.getField("lowMemory").set(memInfo, lowMemory)
        }
    }

    /**
     * Sets up all capabilities to pass (the happy path defaults).
     */
    private fun stubAllCapabilitiesPass() {
        stubAccessibilityEnabled(true)
        stubNotificationListenerEnabled(true)
        stubRecorderInstalled(true)
        stubSufficientRam(true)
        // Models dir already has a file (from setUp)
        // Storage: externalDir exists (from setUp), StatFs will be mocked at OS level
        // For storage, we mock the appSettingsRepository threshold low enough
        every { appSettingsRepository.minFreeStorageMb } returns flowOf(0)
    }

    @Test
    @DisplayName("All capabilities pass → allPassed = true")
    fun allCapabilitiesPass_allPassedTrue() = runTest {
        stubAllCapabilitiesPass()

        val report = capabilityManager.checkAllCapabilities()

        assertTrue(report.accessibilityEnabled)
        assertTrue(report.notificationListenerEnabled)
        assertTrue(report.recorderInstalled)
        assertTrue(report.modelValid)
        assertTrue(report.sufficientRam)
        assertTrue(report.sufficientStorage)
        assertTrue(report.allPassed)
    }

    @Test
    @DisplayName("Accessibility disabled → accessibilityEnabled = false, allPassed = false")
    fun accessibilityDisabled_failsReport() = runTest {
        stubAllCapabilitiesPass()
        stubAccessibilityEnabled(false)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.accessibilityEnabled)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("Notification listener disabled → notificationListenerEnabled = false, allPassed = false")
    fun notificationListenerDisabled_failsReport() = runTest {
        stubAllCapabilitiesPass()
        stubNotificationListenerEnabled(false)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.notificationListenerEnabled)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("Recorder not installed → recorderInstalled = false, allPassed = false")
    fun recorderNotInstalled_failsReport() = runTest {
        stubAllCapabilitiesPass()
        stubRecorderInstalled(false)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.recorderInstalled)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("Configured recorder absent but alternate present → detected and persisted")
    @Suppress("DEPRECATION")
    fun recorderFallbackPackage_detectedAndPersisted() = runTest {
        stubAllCapabilitiesPass()
        // Configured package is missing; the device ships the AOSP recorder instead.
        every {
            packageManager.getPackageInfo(any<String>(), 0)
        } throws PackageManager.NameNotFoundException()
        every {
            packageManager.getPackageInfo("com.android.soundrecorder", 0)
        } returns PackageInfo()

        val report = capabilityManager.checkAllCapabilities()

        assertTrue(report.recorderInstalled)
        coVerify { appSettingsRepository.setRecorderPackage("com.android.soundrecorder") }
    }

    @Test
    @DisplayName("Model file missing → modelValid = false, allPassed = false")
    fun modelFileMissing_failsReport() = runTest {
        stubAllCapabilitiesPass()
        // Delete all files in models dir
        modelsDir.listFiles()?.forEach { it.delete() }

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.modelValid)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("Insufficient RAM → sufficientRam = false, allPassed = false")
    fun insufficientRam_failsReport() = runTest {
        stubAllCapabilitiesPass()
        stubSufficientRam(false)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.sufficientRam)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("System reports memory pressure → sufficientRam = false even with free RAM")
    fun lowMemoryFlag_failsReportDespiteFreeRam() = runTest {
        stubAllCapabilitiesPass()
        // Plenty of free RAM, but the system is actively reclaiming — loading a large
        // model now risks the process being killed.
        stubSufficientRam(sufficient = true, lowMemory = true)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.sufficientRam)
        assertTrue(report.lowMemory)
    }

    @Test
    @DisplayName("Report carries measured figures so a rejection can be diagnosed")
    fun reportCarriesMeasuredFigures() = runTest {
        stubAllCapabilitiesPass()

        val report = capabilityManager.checkAllCapabilities()

        assertEquals(4096L, report.availableRamMb)
        assertEquals(8192L, report.totalRamMb)
    }

    @Test
    @DisplayName("Insufficient storage → sufficientStorage = false, allPassed = false")
    fun insufficientStorage_failsReport() = runTest {
        stubAllCapabilitiesPass()
        // Set threshold impossibly high so real StatFs will fail
        every { appSettingsRepository.minFreeStorageMb } returns flowOf(Int.MAX_VALUE)

        val report = capabilityManager.checkAllCapabilities()

        assertFalse(report.sufficientStorage)
        assertFalse(report.allPassed)
    }

    @Test
    @DisplayName("StateFlows update correctly after checkAllCapabilities()")
    fun stateFlowsUpdateCorrectly() = runTest {
        // Initially all unknown
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.accessibilityState.value)
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.notificationListenerState.value)
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.recorderState.value)
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.modelState.value)
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.ramState.value)
        assertEquals(CapabilityState.UNKNOWN, capabilityManager.storageState.value)

        // Run with accessibility disabled, rest passing
        stubAllCapabilitiesPass()
        stubAccessibilityEnabled(false)

        capabilityManager.checkAllCapabilities()

        // Verify state flows reflect the results
        assertEquals(CapabilityState.UNAVAILABLE, capabilityManager.accessibilityState.value)
        assertEquals(CapabilityState.AVAILABLE, capabilityManager.notificationListenerState.value)
        assertEquals(CapabilityState.AVAILABLE, capabilityManager.recorderState.value)
        assertEquals(CapabilityState.AVAILABLE, capabilityManager.modelState.value)
        assertEquals(CapabilityState.AVAILABLE, capabilityManager.ramState.value)
        assertEquals(CapabilityState.AVAILABLE, capabilityManager.storageState.value)
    }
}
