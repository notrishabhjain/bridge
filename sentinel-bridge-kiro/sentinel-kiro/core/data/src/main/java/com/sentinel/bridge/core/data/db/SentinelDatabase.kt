package com.sentinel.bridge.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sentinel.bridge.core.data.db.dao.CapabilityProfileDao
import com.sentinel.bridge.core.data.db.dao.LogEntryDao
import com.sentinel.bridge.core.data.db.dao.PipelineResultDao
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.CapabilityProfileEntity
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import com.sentinel.bridge.core.data.db.entity.PipelineResultEntity
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity

/**
 * Room database for the Sentinel AI Bridge application.
 *
 * Stores pipeline sessions, structured log entries, capability profiles,
 * and pipeline results. Uses WAL journal mode (Room default on API 16+)
 * and enforces foreign key constraints via entity annotations.
 *
 * Schema is exported to `schemas/` for migration testing and version tracking.
 */
@Database(
    entities = [
        PipelineSessionEntity::class,
        LogEntryEntity::class,
        CapabilityProfileEntity::class,
        PipelineResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SentinelDatabase : RoomDatabase() {

    /**
     * Provides access to [PipelineSessionEntity] operations.
     */
    abstract fun pipelineSessionDao(): PipelineSessionDao

    /**
     * Provides access to [LogEntryEntity] operations.
     */
    abstract fun logEntryDao(): LogEntryDao

    /**
     * Provides access to [CapabilityProfileEntity] operations.
     */
    abstract fun capabilityProfileDao(): CapabilityProfileDao

    /**
     * Provides access to [PipelineResultEntity] operations.
     */
    abstract fun pipelineResultDao(): PipelineResultDao

    companion object {
        /** Database file name used when building the Room instance. */
        const val DATABASE_NAME = "sentinel_bridge.db"
    }
}
