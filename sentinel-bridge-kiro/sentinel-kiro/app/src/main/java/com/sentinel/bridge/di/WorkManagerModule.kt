package com.sentinel.bridge.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WorkManager initialization with [HiltWorkerFactory] support.
 *
 * Provides a [Configuration] that uses Hilt's worker factory for assisted injection
 * into [PipelineWorker] and any future HiltWorker-annotated workers.
 *
 * The application must disable the default WorkManager initializer in AndroidManifest.xml
 * and call [WorkManager.initialize] with this configuration in [SentinelApplication.onCreate].
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    /**
     * Provides the WorkManager [Configuration] with Hilt worker factory integration.
     *
     * Uses [HiltWorkerFactory] so that workers annotated with `@HiltWorker` can
     * receive constructor-injected dependencies via `@AssistedInject`.
     *
     * @param workerFactory Hilt-generated worker factory for assisted injection.
     * @return The WorkManager configuration.
     */
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory
    ): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    /**
     * Provides the singleton [WorkManager] instance.
     *
     * @param context Application context.
     * @return The WorkManager instance for the application.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
}
