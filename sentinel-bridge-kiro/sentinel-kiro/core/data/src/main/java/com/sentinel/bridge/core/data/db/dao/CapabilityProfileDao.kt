package com.sentinel.bridge.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sentinel.bridge.core.data.db.entity.CapabilityProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [CapabilityProfileEntity].
 *
 * Provides insert, query, and deletion operations for device capability profiles.
 * Write operations use `suspend` functions for structured concurrency.
 * Read operations return [Flow] for reactive observation, enabling the
 * [CapabilityManager] to detect profile changes in real time.
 *
 * Only the latest profile is typically relevant for pipeline pre-flight checks,
 * but historical profiles are retained for mismatch detection after system updates.
 */
@Dao
interface CapabilityProfileDao {

    /**
     * Inserts a new capability profile into the database.
     * Each profile captures the device/Recorder state at a point in time.
     *
     * @param profile The [CapabilityProfileEntity] to insert.
     */
    @Insert
    suspend fun insert(profile: CapabilityProfileEntity)

    /**
     * Retrieves the most recently recorded capability profile.
     *
     * @return The latest [CapabilityProfileEntity] by [CapabilityProfileEntity.recordedAt], or null if none exist.
     */
    @Query("SELECT * FROM capability_profiles ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatest(): CapabilityProfileEntity?

    /**
     * Observes the most recently recorded capability profile reactively.
     * Emits a new value whenever capability profiles are inserted or deleted.
     *
     * @return A [Flow] emitting the latest [CapabilityProfileEntity] or null.
     */
    @Query("SELECT * FROM capability_profiles ORDER BY recordedAt DESC LIMIT 1")
    fun observeLatest(): Flow<CapabilityProfileEntity?>

    /**
     * Observes all capability profiles ordered by recording time (newest first).
     * Emits a new list whenever profiles change.
     *
     * @return A [Flow] emitting the full list of [CapabilityProfileEntity].
     */
    @Query("SELECT * FROM capability_profiles ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<CapabilityProfileEntity>>

    /**
     * Deletes a specific capability profile by its auto-generated ID.
     *
     * @param id The primary key of the profile to delete.
     */
    @Query("DELETE FROM capability_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
