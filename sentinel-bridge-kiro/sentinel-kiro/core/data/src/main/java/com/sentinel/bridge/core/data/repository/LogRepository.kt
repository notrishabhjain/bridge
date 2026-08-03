package com.sentinel.bridge.core.data.repository

import com.sentinel.bridge.core.data.db.dao.LogEntryDao
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that wraps [PipelineSessionDao] and [LogEntryDao] to provide
 * session and log entry persistence with automatic rotation enforcement.
 *
 * The rotation policy ensures that no more than [MAX_SESSIONS] pipeline sessions
 * are retained in the database. After every session insert, the repository checks
 * the total count and deletes the oldest sessions exceeding the limit. Due to
 * CASCADE foreign keys on [LogEntryEntity] and PipelineResultEntity, associated
 * child records are automatically cleaned up when a session is deleted.
 *
 * All write operations are suspend functions for structured concurrency.
 * Read operations return [Flow] for reactive observation of database changes.
 */
@Singleton
class LogRepository @Inject constructor(
    private val pipelineSessionDao: PipelineSessionDao,
    private val logEntryDao: LogEntryDao
) {

    companion object {
        /**
         * Maximum number of pipeline sessions to retain in the database.
         * Sessions beyond this limit are deleted oldest-first during rotation.
         */
        const val MAX_SESSIONS = 100
    }

    /**
     * Inserts a new pipeline session and enforces the rotation policy.
     *
     * After inserting the session, this method delegates to [insertWithRotation]
     * to ensure the total session count does not exceed [MAX_SESSIONS].
     *
     * @param session The [PipelineSessionEntity] to insert.
     */
    suspend fun insertSession(session: PipelineSessionEntity) {
        insertWithRotation(session)
    }

    /**
     * Inserts a pipeline session and rotates old sessions if the count exceeds [MAX_SESSIONS].
     *
     * The rotation logic:
     * 1. Inserts the session via [PipelineSessionDao.insert].
     * 2. Queries the total session count.
     * 3. If count > [MAX_SESSIONS], deletes the oldest `(count - MAX_SESSIONS)` sessions.
     *
     * Uses [Dispatchers.IO] since the operation involves multiple sequential database calls
     * that benefit from being on the IO dispatcher for optimal thread utilization.
     *
     * CASCADE foreign keys on log_entries and pipeline_results tables ensure that
     * all associated child records are automatically deleted when a session is removed.
     *
     * @param session The [PipelineSessionEntity] to insert.
     */
    suspend fun insertWithRotation(session: PipelineSessionEntity) {
        withContext(Dispatchers.IO) {
            pipelineSessionDao.insert(session)
            val count = pipelineSessionDao.count()
            if (count > MAX_SESSIONS) {
                pipelineSessionDao.deleteOldest(count - MAX_SESSIONS)
            }
        }
    }

    /**
     * Inserts a single log entry into the database.
     *
     * @param entry The [LogEntryEntity] to insert.
     */
    suspend fun insertLogEntry(entry: LogEntryEntity) {
        logEntryDao.insert(entry)
    }

    /**
     * Retrieves a single pipeline session by its unique identifier.
     *
     * @param sessionId The UUID of the session to retrieve.
     * @return The matching [PipelineSessionEntity], or null if not found.
     */
    suspend fun getSessionById(sessionId: String): PipelineSessionEntity? {
        return pipelineSessionDao.getById(sessionId)
    }

    /**
     * Observes the most recent pipeline sessions up to the specified limit.
     * Emits a new list whenever any session row changes.
     *
     * @param limit Maximum number of sessions to return.
     * @return A [Flow] emitting the most recent [PipelineSessionEntity] list ordered by creation time descending.
     */
    fun observeRecentSessions(limit: Int): Flow<List<PipelineSessionEntity>> {
        return pipelineSessionDao.observeRecent(limit)
    }

    /**
     * Observes all log entries for a given session ordered by timestamp ascending.
     * Emits a new list whenever log entries for the session change.
     *
     * @param sessionId The UUID of the pipeline session.
     * @return A [Flow] emitting the list of [LogEntryEntity] for the session.
     */
    fun getLogsBySession(sessionId: String): Flow<List<LogEntryEntity>> {
        return logEntryDao.observeBySession(sessionId)
    }
}
