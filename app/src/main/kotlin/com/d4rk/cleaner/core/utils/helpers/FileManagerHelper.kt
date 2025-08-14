package com.d4rk.cleaner.core.utils.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.d4rk.cleaner.R
import java.io.File

object FileManagerHelper {

    private fun launchFileManager(context: Context, pm: PackageManager): Boolean {
        val launchPackages = listOf(
            "com.google.android.apps.nbu.files",
            "com.android.documentsui"
        )
        for (pkg in launchPackages) {
            pm.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
                return true
            }
        }
        return false
    }

    fun openFile(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.no_application_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.onFailure { exception ->
            when (exception) {
                is IllegalArgumentException -> Toast.makeText(
                    context,
                    context.getString(R.string.something_went_wrong),
                    Toast.LENGTH_SHORT
                ).show()

                else -> throw exception
            }
        }
    }

    private fun openFolder(
        context: Context,
        folder: File,
        onNotStarted: () -> Unit,
        onFailure: () -> Unit
    ) {
        val pm = context.packageManager
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                folder
            )

            val baseIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "resource/folder")
            baseIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            )

            val explorerPackages = listOf(
                "com.google.android.apps.nbu.files",
                "com.android.documentsui",
                "com.sec.android.app.myfiles",
                "com.mi.android.fileexplorer"
            )

            var started = false
            for (pkg in explorerPackages) {
                val intent = Intent(baseIntent).setPackage(pkg)
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    started = true
                    break
                }
            }

            if (!started && baseIntent.resolveActivity(pm) != null) {
                context.startActivity(baseIntent)
                started = true
            }

            if (!started) {
                if (!launchFileManager(context, pm)) {
                    onNotStarted()
                }
            }
        }.onFailure {
            if (!launchFileManager(context, pm)) {
                onFailure()
            }
        }
    }

    fun openFolderOrSettings(context: Context, folder: File) {
        val pm = context.packageManager
        openFolder(
            context,
            folder,
            onNotStarted = {
                val settingsIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (settingsIntent.resolveActivity(pm) != null) {
                    context.startActivity(settingsIntent)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.no_application_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onFailure = {
                val settingsIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (settingsIntent.resolveActivity(pm) != null) {
                    context.startActivity(settingsIntent)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.something_went_wrong),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    fun openFolderOrToast(context: Context, folder: File) {
        openFolder(
            context,
            folder,
            onNotStarted = {
                Toast.makeText(
                    context,
                    context.getString(R.string.no_application_found),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onFailure = {
                Toast.makeText(
                    context,
                    context.getString(R.string.something_went_wrong),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
