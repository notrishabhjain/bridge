package com.sentinel.bridge.di

import android.content.Context
import androidx.room.Room
import com.sentinel.bridge.core.data.db.SentinelDatabase
import com.sentinel.bridge.core.data.db.dao.CapabilityProfileDao
import com.sentinel.bridge.core.data.db.dao.LogEntryDao
import com.sentinel.bridge.core.data.db.dao.PipelineResultDao
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database instance and all DAO accessors.
 *
 * The database is created as a singleton with WAL journal mode (Room default on API 16+)
 * and foreign key enforcement via entity annotations. Schema is exported to `schemas/`
 * for migration testing.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton [SentinelDatabase] Room instance.
     *
     * @param context Application context for database file creation.
     * @return The configured Room database instance.
     */
    @Provides
    @Singleton
    fun provideSentinelDatabase(
        @ApplicationContext context: Context
    ): SentinelDatabase {
        return Room.databaseBuilder(
            context,
            SentinelDatabase::class.java,
            SentinelDatabase.DATABASE_NAME
        ).build()
    }

    /**
     * Provides the [PipelineSessionDao] from the database.
     */
    @Provides
    fun providePipelineSessionDao(database: SentinelDatabase): PipelineSessionDao {
        return database.pipelineSessionDao()
    }

    /**
     * Provides the [LogEntryDao] from the database.
     */
    @Provides
    fun provideLogEntryDao(database: SentinelDatabase): LogEntryDao {
        return database.logEntryDao()
    }

    /**
     * Provides the [CapabilityProfileDao] from the database.
     */
    @Provides
    fun provideCapabilityProfileDao(database: SentinelDatabase): CapabilityProfileDao {
        return database.capabilityProfileDao()
    }

    /**
     * Provides the [PipelineResultDao] from the database.
     */
    @Provides
    fun providePipelineResultDao(database: SentinelDatabase): PipelineResultDao {
        return database.pipelineResultDao()
    }
}
