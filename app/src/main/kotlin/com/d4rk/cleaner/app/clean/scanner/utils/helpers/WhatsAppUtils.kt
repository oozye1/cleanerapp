package com.d4rk.cleaner.app.clean.scanner.utils.helpers

import android.content.Context
import android.os.Environment
import com.d4rk.cleaner.app.clean.whatsapp.utils.constants.WhatsAppMediaConstants
import java.io.File

/**
 * Returns the base WhatsApp media directory or `null` if not found.
 * The legacy `<root>/WhatsApp/Media` path is checked first. If the directory
 * does not exist, the scoped storage location under
 * `<root>/Android/media/com.whatsapp/WhatsApp/Media` is checked.
 *
 * When neither directory exists, `null` is returned, signalling that WhatsApp
 * media is not accessible on the device.
 */
fun getWhatsAppMediaDirs(context: Context): File? {
    val root = Environment.getExternalStorageDirectory()
    val legacy = File(root, "WhatsApp/Media")
    if (legacy.exists()) return legacy
    val scoped = File(root, "Android/media/com.whatsapp/WhatsApp/Media")
    return scoped.takeIf { it.exists() }
}

/**
 * Provides a summary of WhatsApp media files. If the WhatsApp media directories
 * are absent, an empty triple is returned.
 */
fun getWhatsAppMediaSummary(context: Context): Triple<List<File>, List<File>, List<File>> {
    val mediaDir = getWhatsAppMediaDirs(context)
        ?: return Triple(emptyList(), emptyList(), emptyList())
    fun list(dirName: String): List<File> {
        val dir = File(mediaDir, dirName)
        return dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    val images =
        WhatsAppMediaConstants.DIRECTORIES[WhatsAppMediaConstants.IMAGES]?.let(::list) ?: emptyList()
    val videos =
        WhatsAppMediaConstants.DIRECTORIES[WhatsAppMediaConstants.VIDEOS]?.let(::list) ?: emptyList()
    val docs =
        WhatsAppMediaConstants.DIRECTORIES[WhatsAppMediaConstants.DOCUMENTS]?.let(::list) ?: emptyList()
    return Triple(images, videos, docs)
}
