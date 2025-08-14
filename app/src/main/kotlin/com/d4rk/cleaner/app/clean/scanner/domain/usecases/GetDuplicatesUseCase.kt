package com.d4rk.cleaner.app.clean.scanner.domain.usecases

import com.d4rk.android.libs.apptoolkit.core.di.DispatcherProvider
import com.d4rk.cleaner.app.clean.scanner.domain.data.model.ui.FileEntry
import com.d4rk.cleaner.core.utils.extensions.partialMd5
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class GetDuplicatesUseCase(
    private val dispatchers: DispatcherProvider
) {
    private val hashCache = ConcurrentHashMap<String, String>()
    private val chunkSize = 100

    suspend operator fun invoke(files: List<File>): List<List<FileEntry>> {
        val candidates = files.filter { it.isFile }
            .groupBy { it.length() to it.lastModified() }
            .values
            .filter { it.size > 1 }
            .flatten()

        if (candidates.isEmpty()) return emptyList()

        val hashed = coroutineScope {
            candidates.chunked(chunkSize).map { chunk ->
                async {
                    withContext(dispatchers.io) {
                        chunk.mapNotNull { file ->
                            val key = "${file.absolutePath}:${file.lastModified()}"
                            val hash = hashCache[key] ?: file.partialMd5()?.also { hashCache[key] = it }
                            hash?.let { it to file }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        return hashed.groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }
            .map { group ->
                group.map { f -> FileEntry(f.absolutePath, f.length(), f.lastModified()) }
            }
    }
}

