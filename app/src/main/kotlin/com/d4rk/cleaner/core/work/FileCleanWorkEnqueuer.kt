package com.d4rk.cleaner.core.work

import android.app.Application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.d4rk.cleaner.app.clean.scanner.work.FileCleanupWorker
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Helper that chunks file paths, enqueues [FileCleanupWorker] jobs and persists the work ID.
 */
class FileCleanWorkEnqueuer(
    private val application: Application,
    private val workManager: WorkManager = WorkManager.getInstance(application)
) {

    sealed class Result {
        object AlreadyRunning : Result()
        data class Enqueued(val id: UUID) : Result()
        data class Error(val throwable: Throwable) : Result()
    }

    /**
     * Enqueue cleanup work for the given paths and action.
     */
    suspend fun enqueue(
        paths: Collection<String>,
        action: String,
        getWorkId: suspend () -> String?,
        saveWorkId: suspend (String) -> Unit,
        clearWorkId: suspend () -> Unit
    ): Result {
        val activeId = getWorkId()
        if (activeId != null) {
            val info = workManager.getWorkInfoByIdFlow(UUID.fromString(activeId)).first()
            if (info != null && !info.state.isFinished) {
                return Result.AlreadyRunning
            } else {
                clearWorkId()
            }
        }

        if (paths.isEmpty()) {
            return Result.Error(IllegalArgumentException("No paths provided"))
        }

        val chunks = chunkPaths(paths, action)
        var continuation: androidx.work.WorkContinuation? = null
        val requestIds = mutableListOf<UUID>()
        for (chunk in chunks) {
            val request = OneTimeWorkRequestBuilder<FileCleanupWorker>()
                .setInputData(
                    workDataOf(
                        FileCleanupWorker.KEY_ACTION to action,
                        FileCleanupWorker.KEY_PATHS to chunk.toTypedArray()
                    )
                ).build()
            requestIds += request.id
            continuation = continuation?.then(request) ?: workManager.beginWith(request)
        }

        val finalRequestId = requestIds.lastOrNull() ?: return Result.Error(IllegalStateException("No work created"))

        return runCatching {
            continuation?.enqueue()
            saveWorkId(finalRequestId.toString())
            Result.Enqueued(finalRequestId)
        }.getOrElse { t ->
            requestIds.forEach { workManager.cancelWorkById(it) }
            Result.Error(t)
        }
    }

    private fun chunkPaths(paths: Collection<String>, action: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        for (path in paths) {
            current += path
            val tooBig = runCatching {
                Data.Builder()
                    .putString(FileCleanupWorker.KEY_ACTION, action)
                    .putStringArray(FileCleanupWorker.KEY_PATHS, current.toTypedArray())
                    .build()
                    .toByteArray()
                    .size > Data.MAX_DATA_BYTES
            }.getOrDefault(true)
            val overLimit = current.size > FileCleanupWorker.MAX_PATHS_PER_WORKER
            if (tooBig || overLimit) {
                val last = current.removeAt(current.lastIndex)
                if (current.isNotEmpty()) {
                    result += current.toList()
                }
                current.clear()
                current += last
            }
        }
        if (current.isNotEmpty()) {
            result += current.toList()
        }
        return result
    }
}

