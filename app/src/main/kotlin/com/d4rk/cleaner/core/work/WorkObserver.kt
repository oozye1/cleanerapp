package com.d4rk.cleaner.core.work

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

object WorkObserver {
    fun observe(
        scope: CoroutineScope,
        workManager: WorkManager,
        workId: UUID,
        dispatcher: CoroutineDispatcher,
        clearWorkId: suspend () -> Unit,
        onRunning: suspend () -> Unit = {},
        onSuccess: suspend (WorkInfo) -> Unit = {},
        onFailed: suspend () -> Unit = {},
        onCancelled: suspend () -> Unit = {},
        onProgress: suspend (WorkInfo) -> Unit = {}
    ): Job {
        return scope.launch(dispatcher) {
            workManager.getWorkInfoByIdFlow(workId).collectLatest { info ->
                info?.let { onProgress(it) }
                when (info?.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED -> onRunning()
                    WorkInfo.State.SUCCEEDED -> {
                        clearWorkId()
                        onSuccess(info)
                        cancel()
                    }
                    WorkInfo.State.FAILED -> {
                        clearWorkId()
                        onFailed()
                        cancel()
                    }
                    WorkInfo.State.CANCELLED -> {
                        clearWorkId()
                        onCancelled()
                        cancel()
                    }
                    null -> {
                        clearWorkId()
                        onCancelled()
                        cancel()
                    }
                }
            }
        }
    }
}

