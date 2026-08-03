package com.sentinel.bridge.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [LogEntryEntity].
 *
 * Provides insert, query, and deletion operations for structured log entries.
 * Write operations use `suspend` functions for structured concurrency.
 * Read operations return [Flow] for reactive observation of log changes,
 * enabling real-time log viewing in debug UIs.
 *
 * Log entries are cascade-deleted when their parent [PipelineSessionEntity]
 * is removed, supporting the 100-session rotation policy.
 */
@Dao
interface LogEntryDao {

    /**
     * Inserts a single log entry into the database.
     *
     * @param entry The [LogEntryEntity] to insert.
     */
    @Insert
    suspend fun insert(entry: LogEntryEntity)

    /**
     * Inserts multiple log entries in a single transaction.
     * Useful for batch-logging multiple events from a single pipeline stage.
     *
     * @param entries The list of [LogEntryEntity] to insert.
     */
    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    /**
     * Observes all log entries for a given session ordered by timestamp ascending.
     * Emits a new list whenever log entries for the session change.
     *
     * @param sessionId The UUID of the pipeline session.
     * @return A [Flow] emitting the list of [LogEntryEntity] for the session.
     */
    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<LogEntryEntity>>

    /**
     * Retrieves all log entries for a given session ordered by timestamp ascending.
     *
     * @param sessionId The UUID of the pipeline session.
     * @return The list of [LogEntryEntity] for the session.
     */
    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<LogEntryEntity>

    /**
     * Observes all log entries matching a specific severity level, ordered by timestamp descending.
     * Useful for filtering ERROR or WARN entries across all sessions.
     *
     * @param level The log severity level to filter by (e.g., "INFO", "WARN", "ERROR").
     * @return A [Flow] emitting the list of [LogEntryEntity] matching the level.
     */
    @Query("SELECT * FROM log_entries WHERE level = :level ORDER BY timestamp DESC")
    fun observeByLevel(level: String): Flow<List<LogEntryEntity>>

    /**
     * Deletes all log entries associated with a specific session.
     * Used when manually clearing logs for a session outside of cascade delete.
     *
     * @param sessionId The UUID of the pipeline session whose logs should be deleted.
     */
    @Query("DELETE FROM log_entries WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /**
     * Retrieves the distinct set of session IDs that have log entries.
     * Useful for identifying which sessions have recorded activity.
     *
     * @return A list of unique session IDs present in the log_entries table.
     */
    @Query("SELECT DISTINCT sessionId FROM log_entries")
    suspend fun getDistinctSessionIds(): List<String>
}
