package com.sentinel.bridge.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the DataStore Preferences instance.
 *
 * A single [DataStore] instance is shared between [AppSettingsRepository] and
 * [FeatureFlagsRepository]. Key namespaces are disjoint so no conflicts occur.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val PREFERENCES_NAME = "sentinel_preferences"

    /**
     * Provides the singleton [DataStore]<[Preferences]> instance.
     *
     * Uses [PreferenceDataStoreFactory] to create the preferences file in the
     * application's internal storage directory.
     *
     * @param context Application context for file path resolution.
     * @return The configured DataStore instance.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(PREFERENCES_NAME)
        }
    }
}
