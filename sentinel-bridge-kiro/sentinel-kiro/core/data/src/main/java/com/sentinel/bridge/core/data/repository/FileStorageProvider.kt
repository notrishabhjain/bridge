package com.sentinel.bridge.core.data.repository

import android.content.Context
import android.net.Uri
import com.sentinel.bridge.core.domain.interfaces.StorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-system backed implementation of [StorageProvider].
 *
 * Transcripts are stored as plain-text files at
 * `getExternalFilesDir("transcripts")/<sessionId>.txt`. This is app-private
 * external storage and does not require `MANAGE_EXTERNAL_STORAGE`.
 *
 * All I/O operations run on [Dispatchers.IO] to avoid blocking the calling
 * coroutine context.
 */
@Singleton
class FileStorageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageProvider {

    /**
     * Persists a transcript to the file system.
     *
     * Creates the `transcripts` directory if it does not already exist, then
     * writes [content] encoded as UTF-8 to `<sessionId>.txt`.
     *
     * @param sessionId Unique identifier for the pipeline session.
     * @param content Raw transcript text to persist.
     * @return [Uri] pointing to the stored transcript file.
     */
    override suspend fun saveTranscript(sessionId: String, content: String): Uri {
        return withContext(Dispatchers.IO) {
            val dir = context.getExternalFilesDir("transcripts")
            dir?.mkdirs()
            val file = File(dir, "$sessionId.txt")
            file.writeText(content, Charsets.UTF_8)
            Uri.fromFile(file)
        }
    }

    /**
     * Loads a previously stored transcript from the file system.
     *
     * @param sessionId Unique identifier for the pipeline session.
     * @return Transcript content if the file exists, `null` otherwise.
     */
    override suspend fun loadTranscript(sessionId: String): String? {
        return withContext(Dispatchers.IO) {
            val file = File(context.getExternalFilesDir("transcripts"), "$sessionId.txt")
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        }
    }

    /**
     * Deletes a stored transcript from the file system.
     *
     * No-op if the file does not exist.
     *
     * @param sessionId Unique identifier for the pipeline session whose transcript to delete.
     */
    override suspend fun deleteTranscript(sessionId: String) {
        withContext(Dispatchers.IO) {
            val file = File(context.getExternalFilesDir("transcripts"), "$sessionId.txt")
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
