package com.sentinel.bridge

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Sentinel AI Bridge.
 *
 * Annotated with @HiltAndroidApp to trigger Hilt code generation
 * and serve as the root dependency injection container.
 *
 * Implements [Configuration.Provider] to supply WorkManager with a Hilt-aware
 * [Configuration] that enables `@HiltWorker` assisted injection in [PipelineWorker].
 * The default WorkManager initializer must be disabled in AndroidManifest.xml.
 */
@HiltAndroidApp
class SentinelApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var wmConfiguration: Configuration

    override val workManagerConfiguration: Configuration
        get() = wmConfiguration
}
