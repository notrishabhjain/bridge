package com.sentinel.bridge.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sentinel.bridge.core.data.db.entity.PipelineResultEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [PipelineResultEntity].
 *
 * Provides insert, query, and deletion operations for pipeline result records.
 * Write operations use `suspend` functions for structured concurrency.
 * Read operations return [Flow] for reactive observation, enabling UI layers
 * to display results as soon as they are persisted.
 *
 * Results are cascade-deleted when their parent [PipelineSessionEntity] is removed,
 * supporting the 100-session rotation policy.
 */
@Dao
interface PipelineResultDao {

    /**
     * Inserts a pipeline result or replaces an existing one with the same [PipelineResultEntity.sessionId].
     * Uses REPLACE strategy to handle re-processing of a session without explicit delete.
     *
     * @param result The [PipelineResultEntity] to insert or replace.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: PipelineResultEntity)

    /**
     * Retrieves a pipeline result by its associated session ID.
     *
     * @param sessionId The UUID of the pipeline session.
     * @return The matching [PipelineResultEntity], or null if no result exists for this session.
     */
    @Query("SELECT * FROM pipeline_results WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): PipelineResultEntity?

    /**
     * Observes a pipeline result by its associated session ID reactively.
     * Emits a new value whenever the result row changes or is inserted.
     *
     * @param sessionId The UUID of the pipeline session.
     * @return A [Flow] emitting the matching [PipelineResultEntity] or null.
     */
    @Query("SELECT * FROM pipeline_results WHERE sessionId = :sessionId")
    fun observeBySessionId(sessionId: String): Flow<PipelineResultEntity?>

    /**
     * Observes all pipeline results ordered by creation time (newest first).
     * Emits a new list whenever any result row changes.
     *
     * @return A [Flow] emitting the full list of [PipelineResultEntity].
     */
    @Query("SELECT * FROM pipeline_results ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PipelineResultEntity>>

    /**
     * Observes the most recent pipeline results up to the specified limit.
     * Emits a new list whenever any result row changes.
     *
     * @param limit Maximum number of results to return.
     * @return A [Flow] emitting the most recent [PipelineResultEntity] list.
     */
    @Query("SELECT * FROM pipeline_results ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PipelineResultEntity>>

    /**
     * Deletes a pipeline result by its associated session ID.
     *
     * @param sessionId The UUID of the pipeline session whose result should be deleted.
     */
    @Query("DELETE FROM pipeline_results WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
