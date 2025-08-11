package com.d4rk.cleaner.app.clean.whatsapp.summary.data

import android.app.Application
import android.os.Environment
import android.text.format.Formatter
import com.d4rk.cleaner.app.clean.whatsapp.summary.domain.model.DeleteResult
import com.d4rk.cleaner.app.clean.whatsapp.summary.domain.model.DirectorySummary
import com.d4rk.cleaner.app.clean.whatsapp.summary.domain.model.WhatsAppMediaSummary
import com.d4rk.cleaner.app.clean.whatsapp.summary.domain.repository.WhatsAppCleanerRepository
import com.d4rk.cleaner.app.clean.whatsapp.utils.constants.WhatsAppMediaConstants
import com.d4rk.cleaner.core.utils.helpers.FileDeletionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhatsAppCleanerRepositoryImpl(private val application: Application) :
    WhatsAppCleanerRepository {
    /**
     * Locate the base WhatsApp media directory. Returns `null` when no known
     * directories are found, indicating that WhatsApp media is not available on
     * the device. Both the legacy and scoped-storage locations for official,
     * business, and OEM dual-app variants are checked.
     */
    private fun getWhatsAppMediaDir(): File? {
        val roots = mutableListOf<File>()
        application.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile?.let { roots += it }
        roots += Environment.getExternalStorageDirectory()

        roots.distinct().forEach { root ->
            // Legacy top-level directories
            listOf("WhatsApp", "WhatsApp Business").forEach { name ->
                val legacy = File(root, "$name/Media")
                if (legacy.exists()) return legacy
            }

            // Scoped storage packages that start with "com.whatsapp"
            val mediaRoot = File(root, "Android/media")
            mediaRoot.listFiles()?.forEach { pkgDir ->
                if (pkgDir.name.startsWith("com.whatsapp")) {
                    pkgDir.listFiles()?.forEach { waDir ->
                        if (waDir.isDirectory && waDir.name.startsWith("WhatsApp")) {
                            val media = File(waDir, "Media")
                            if (media.exists()) return media
                        }
                    }
                }
            }
        }
        return null
    }

    override suspend fun getMediaSummary(): WhatsAppMediaSummary = withContext(Dispatchers.IO) {
        val base = getWhatsAppMediaDir() ?: return@withContext WhatsAppMediaSummary()

        fun collect(name: String): DirectorySummary {
            val dir = File(base, name)
            if (!dir.exists()) return DirectorySummary()
            val files = dir.walkTopDown()
                .filter { it.isFile && it.name != ".nomedia" }
            var count = 0
            var size = 0L
            files.forEach {
                count++
                size += it.length()
            }
            val formatted = Formatter.formatFileSize(application, size)
            return DirectorySummary(count, size, formatted)
        }

        val directories = WhatsAppMediaConstants.DIRECTORIES

        val collected = directories.mapValues { (_, dirName) -> collect(dirName) }

        val images = collected.getValue(WhatsAppMediaConstants.IMAGES)
        val videos = collected.getValue(WhatsAppMediaConstants.VIDEOS)
        val docs = collected.getValue(WhatsAppMediaConstants.DOCUMENTS)
        val audios = collected.getValue(WhatsAppMediaConstants.AUDIOS)
        val statuses = collected.getValue(WhatsAppMediaConstants.STATUSES)
        val voiceNotes = collected.getValue(WhatsAppMediaConstants.VOICE_NOTES)
        val videoNotes = collected.getValue(WhatsAppMediaConstants.VIDEO_NOTES)
        val gifs = collected.getValue(WhatsAppMediaConstants.GIFS)
        val wallpapers = collected.getValue(WhatsAppMediaConstants.WALLPAPERS)
        val stickers = collected.getValue(WhatsAppMediaConstants.STICKERS)
        val profile = collected.getValue(WhatsAppMediaConstants.PROFILE_PHOTOS)

        val totalSize = collected.values.sumOf { it.totalBytes }
        val totalFormatted = Formatter.formatFileSize(application, totalSize)

        WhatsAppMediaSummary(
            images = images,
            videos = videos,
            documents = docs,
            audios = audios,
            statuses = statuses,
            voiceNotes = voiceNotes,
            videoNotes = videoNotes,
            gifs = gifs,
            wallpapers = wallpapers,
            stickers = stickers,
            profilePhotos = profile,
            formattedTotalSize = totalFormatted,
        )
    }

    override suspend fun deleteFiles(files: Collection<File>): DeleteResult = withContext(Dispatchers.IO) {
        val results = FileDeletionHelper.deleteFiles(files, application.contentResolver)
        val deletedCount = results.count { it.success }
        val failed = results.filter { !it.success }.map { it.file.path }
        DeleteResult(
            deletedCount = deletedCount,
            failedPaths = failed
        )
    }

    override suspend fun listMediaFiles(type: String, offset: Int, limit: Int): List<File> =
        withContext(Dispatchers.IO) {
            val base = getWhatsAppMediaDir() ?: return@withContext emptyList<File>()
            val dirName =
                WhatsAppMediaConstants.DIRECTORIES[type] ?: return@withContext emptyList<File>()
            val dir = File(base, dirName)
            if (!dir.exists()) return@withContext emptyList()
            dir.walkTopDown()
                .filter { it.isFile && it.name != ".nomedia" }
                .drop(offset)
                .take(limit)
                .toList()
        }

}
