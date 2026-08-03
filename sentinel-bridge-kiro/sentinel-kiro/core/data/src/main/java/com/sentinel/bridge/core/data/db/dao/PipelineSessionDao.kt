package com.sentinel.bridge.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [PipelineSessionEntity].
 *
 * Provides full CRUD operations and reactive queries for pipeline sessions.
 * Write operations use `suspend` functions for structured concurrency.
 * Read operations return [Flow] for reactive observation of database changes.
 *
 * Supports session rotation via [deleteOldest] and stage-level updates
 * via [updateStage] and [updateCompletion] for efficient partial writes
 * during pipeline execution.
 */
@Dao
interface PipelineSessionDao {

    /**
     * Inserts a new pipeline session or replaces an existing one with the same [PipelineSessionEntity.sessionId].
     *
     * @param session The session entity to insert or replace.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PipelineSessionEntity)

    /**
     * Updates an existing pipeline session entity in the database.
     *
     * @param session The session entity with updated fields.
     */
    @Update
    suspend fun update(session: PipelineSessionEntity)

    /**
     * Retrieves a single pipeline session by its unique identifier.
     *
     * @param sessionId The UUID of the session to retrieve.
     * @return The matching [PipelineSessionEntity], or null if not found.
     */
    @Query("SELECT * FROM pipeline_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): PipelineSessionEntity?

    /**
     * Observes a single pipeline session by its unique identifier reactively.
     * Emits a new value whenever the session row changes.
     *
     * @param sessionId The UUID of the session to observe.
     * @return A [Flow] emitting the matching [PipelineSessionEntity] or null.
     */
    @Query("SELECT * FROM pipeline_sessions WHERE sessionId = :sessionId")
    fun observeById(sessionId: String): Flow<PipelineSessionEntity?>

    /**
     * Observes all pipeline sessions ordered by creation time (newest first).
     * Emits a new list whenever any session row changes.
     *
     * @return A [Flow] emitting the full list of [PipelineSessionEntity] ordered descending by [PipelineSessionEntity.createdAt].
     */
    @Query("SELECT * FROM pipeline_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PipelineSessionEntity>>

    /**
     * Observes the most recent pipeline sessions up to the specified limit.
     * Emits a new list whenever any session row changes.
     *
     * @param limit Maximum number of sessions to return.
     * @return A [Flow] emitting the most recent [PipelineSessionEntity] list.
     */
    @Query("SELECT * FROM pipeline_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PipelineSessionEntity>>

    /**
     * Returns the total number of pipeline sessions stored in the database.
     *
     * @return The count of session rows.
     */
    @Query("SELECT COUNT(*) FROM pipeline_sessions")
    suspend fun count(): Int

    /**
     * Deletes a single pipeline session by its unique identifier.
     * Associated log entries and results are cascade-deleted via foreign keys.
     *
     * @param sessionId The UUID of the session to delete.
     */
    @Query("DELETE FROM pipeline_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)

    /**
     * Deletes the oldest sessions to enforce the 100-session rotation policy.
     * Sessions are ordered by [PipelineSessionEntity.createdAt] ascending, and the
     * first [count] are removed.
     *
     * @param count Number of oldest sessions to delete.
     */
    @Query("DELETE FROM pipeline_sessions WHERE sessionId IN (SELECT sessionId FROM pipeline_sessions ORDER BY createdAt ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    /**
     * Updates only the current stage and updatedAt timestamp for a session.
     * Used during pipeline execution to persist state transitions without
     * rewriting the entire entity.
     *
     * @param sessionId The UUID of the session to update.
     * @param stage The new [PipelineStage][com.sentinel.bridge.core.domain.model.PipelineStage] name.
     * @param updatedAt Epoch milliseconds of the update.
     */
    @Query("UPDATE pipeline_sessions SET currentStage = :stage, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateStage(sessionId: String, stage: String, updatedAt: Long)

    /**
     * Updates a session upon pipeline completion or failure.
     * Sets the final stage, timestamps, and optional error details in a single query.
     *
     * @param sessionId The UUID of the session to update.
     * @param stage The final [PipelineStage][com.sentinel.bridge.core.domain.model.PipelineStage] name (e.g., COMPLETE or the failing stage).
     * @param updatedAt Epoch milliseconds of the update.
     * @param completedAt Epoch milliseconds when the pipeline completed or failed.
     * @param errorCode Structured error code, or null on success.
     * @param errorCategory The [ErrorCategory][com.sentinel.bridge.core.domain.model.ErrorCategory] name, or null on success.
     * @param errorMessage Human-readable error description, or null on success.
     */
    @Query("UPDATE pipeline_sessions SET currentStage = :stage, updatedAt = :updatedAt, completedAt = :completedAt, errorCode = :errorCode, errorCategory = :errorCategory, errorMessage = :errorMessage WHERE sessionId = :sessionId")
    suspend fun updateCompletion(sessionId: String, stage: String, updatedAt: Long, completedAt: Long, errorCode: String?, errorCategory: String?, errorMessage: String?)
}
