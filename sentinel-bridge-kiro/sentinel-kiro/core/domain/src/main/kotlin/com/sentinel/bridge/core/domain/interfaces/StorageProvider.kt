package com.sentinel.bridge.core.domain.interfaces

import android.net.Uri

/**
 * Plugin interface for transcript storage providers.
 *
 * Implementations manage persistence of raw transcripts on the file system.
 * Transcripts are stored outside of Intents (Binder 1MB limit) and referenced
 * by session ID.
 */
interface StorageProvider {

    /**
     * Persists a transcript to storage.
     *
     * @param sessionId Unique identifier for the pipeline session.
     * @param content Raw transcript text to persist.
     * @return [Uri] pointing to the stored transcript file.
     */
    suspend fun saveTranscript(sessionId: String, content: String): Uri

    /**
     * Loads a previously stored transcript.
     *
     * @param sessionId Unique identifier for the pipeline session.
     * @return Transcript content if found, `null` if no transcript exists for the session.
     */
    suspend fun loadTranscript(sessionId: String): String?

    /**
     * Deletes a stored transcript.
     *
     * @param sessionId Unique identifier for the pipeline session whose transcript to delete.
     */
    suspend fun deleteTranscript(sessionId: String)
}
