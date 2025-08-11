package com.d4rk.cleaner.app.auto

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.d4rk.android.libs.apptoolkit.core.domain.model.network.DataState
import com.d4rk.cleaner.app.clean.scanner.domain.data.model.ui.FileTypesData
import com.d4rk.cleaner.app.clean.scanner.domain.`interface`.ScannerRepositoryInterface
import com.d4rk.cleaner.app.clean.scanner.domain.usecases.AnalyzeFilesUseCase
import com.d4rk.cleaner.app.clean.scanner.domain.usecases.DeleteFilesUseCase
import com.d4rk.cleaner.app.clean.scanner.domain.usecases.GetEmptyFoldersUseCase
import com.d4rk.cleaner.app.clean.scanner.domain.usecases.GetFileTypesUseCase
import com.d4rk.cleaner.app.images.utils.ImageHashUtils
import com.d4rk.cleaner.app.settings.cleaning.utils.constants.ExtensionsConstants
import com.d4rk.cleaner.core.utils.constants.datastore.AppDataStoreConstants
import com.d4rk.cleaner.core.data.datastore.DataStore
import com.d4rk.cleaner.core.utils.extensions.partialMd5
import com.d4rk.cleaner.core.utils.helpers.CleaningEventBus
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.time.Duration.Companion.days

class AutoCleanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val analyzeFiles: AnalyzeFilesUseCase by inject()
    private val deleteFiles: DeleteFilesUseCase by inject()
    private val getFileTypes: GetFileTypesUseCase by inject()
    private val getEmptyFolders: GetEmptyFoldersUseCase by inject()
    private val dataStore: DataStore by inject()
    private val repository: ScannerRepositoryInterface by inject()

    override suspend fun doWork(): Result {
        val preferences = dataStore.data.first()

        val autoCleanEnabled =
            preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_AUTO_CLEAN_ENABLED)]
                ?: false
        if (!autoCleanEnabled) return Result.success()

        val frequency =
            preferences[intPreferencesKey(AppDataStoreConstants.DATA_STORE_AUTO_CLEAN_FREQUENCY_DAYS)]
                ?: 7
        val lastScan =
            preferences[longPreferencesKey(AppDataStoreConstants.DATA_STORE_LAST_SCAN_TIMESTAMP)]
                ?: 0L
        val now = System.currentTimeMillis()
        if (frequency <= 0 || now - lastScan < frequency.days.inWholeMilliseconds) {
            return Result.success()
        }

        val files = mutableListOf<File>()
        var errorDuringScan = false
        analyzeFiles().collect { state ->
            when (state) {
                is DataState.Success -> files.add(state.data)
                is DataState.Error -> {
                    errorDuringScan = true
                    return@collect
                }
                else -> {}
            }
        }
        if (errorDuringScan) return Result.success()

        val emptyFolders = mutableListOf<File>()
        errorDuringScan = false
        getEmptyFolders().collect { state ->
            when (state) {
                is DataState.Success -> emptyFolders.add(state.data)
                is DataState.Error -> {
                    errorDuringScan = true
                    return@collect
                }
                else -> {}
            }
        }
        if (errorDuringScan) return Result.success()

        val typesState = getFileTypes().first { it !is DataState.Loading }
        val types = if (typesState is DataState.Success) typesState.data else FileTypesData()

        val prefs = mapOf(
            ExtensionsConstants.IMAGE_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_IMAGE_FILES)] != false),
            ExtensionsConstants.VIDEO_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_VIDEO_FILES)] != false),
            ExtensionsConstants.AUDIO_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_AUDIO_FILES)] != false),
            ExtensionsConstants.OFFICE_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_OFFICE_FILES)] != false),
            ExtensionsConstants.ARCHIVE_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_ARCHIVES)] == true),
            ExtensionsConstants.APK_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_APK_FILES)] == true),
            ExtensionsConstants.FONT_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_FONT_FILES)] != false),
            ExtensionsConstants.WINDOWS_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_WINDOWS_FILES)] != false),
            ExtensionsConstants.EMPTY_FOLDERS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_EMPTY_FOLDERS)] == true),
            ExtensionsConstants.OTHER_EXTENSIONS to (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_OTHER_EXTENSIONS)] != false)
        )
        val includeDuplicates =
            (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DELETE_DUPLICATE_FILES)] == true) &&
                (preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_ENABLE_DUPLICATE_SCAN)] ?: true)
        val deepDuplicateSearch =
            preferences[booleanPreferencesKey(AppDataStoreConstants.DATA_STORE_DEEP_DUPLICATE_SEARCH)] == true
        val toDelete = computeFilesToClean(
            files,
            emptyFolders,
            types,
            prefs,
            includeDuplicates,
            deepDuplicateSearch
        )
        if (toDelete.isEmpty()) return Result.success()

        deleteFiles(repository = repository, files = toDelete, mode = DeleteFilesUseCase.Mode.PERMANENT).collect {}
        dataStore.saveLastScanTimestamp(now)
        CleaningEventBus.notifyCleaned(success = true)
        return Result.success()
    }

    private fun computeFilesToClean(
        scannedFiles: List<File>,
        emptyFolders: List<File>,
        fileTypesData: FileTypesData,
        preferences: Map<String, Boolean>,
        includeDuplicates: Boolean,
        deepDuplicateSearch: Boolean
    ): List<File> {
        val knownExtensions = (fileTypesData.imageExtensions + fileTypesData.videoExtensions +
                fileTypesData.audioExtensions + fileTypesData.officeExtensions + fileTypesData.archiveExtensions +
                fileTypesData.apkExtensions + fileTypesData.fontExtensions + fileTypesData.windowsExtensions).toSet()
        val result = mutableListOf<File>()

        val duplicateGroups = if (includeDuplicates) findDuplicateGroups(
            scannedFiles,
            deepDuplicateSearch,
            fileTypesData.imageExtensions
        ) else emptyList()
        val duplicateFiles =
            if (includeDuplicates) duplicateGroups.flatten().toSet() else emptySet()

        scannedFiles.forEach { file ->
            if (includeDuplicates && file in duplicateFiles) {
                result.add(file)
            } else {
                val extension = file.extension.lowercase()
                val match = when (extension) {
                    in fileTypesData.imageExtensions -> preferences[ExtensionsConstants.IMAGE_EXTENSIONS] == true
                    in fileTypesData.videoExtensions -> preferences[ExtensionsConstants.VIDEO_EXTENSIONS] == true
                    in fileTypesData.audioExtensions -> preferences[ExtensionsConstants.AUDIO_EXTENSIONS] == true
                    in fileTypesData.officeExtensions -> preferences[ExtensionsConstants.OFFICE_EXTENSIONS] == true
                    in fileTypesData.archiveExtensions -> preferences[ExtensionsConstants.ARCHIVE_EXTENSIONS] == true
                    in fileTypesData.apkExtensions -> preferences[ExtensionsConstants.APK_EXTENSIONS] == true
                    in fileTypesData.fontExtensions -> preferences[ExtensionsConstants.FONT_EXTENSIONS] == true
                    in fileTypesData.windowsExtensions -> preferences[ExtensionsConstants.WINDOWS_EXTENSIONS] == true
                    else -> !knownExtensions.contains(extension) && preferences[ExtensionsConstants.OTHER_EXTENSIONS] == true
                }
                if (match) result.add(file)
            }
        }
        if (preferences[ExtensionsConstants.EMPTY_FOLDERS] == true) {
            result.addAll(emptyFolders)
        }
        return result
    }

    private fun findDuplicateGroups(
        files: List<File>,
        deepSearch: Boolean,
        imageExtensions: List<String>
    ): List<List<File>> {
        val hashMap = mutableMapOf<String, MutableList<File>>()
        files.filter { it.isFile }.forEach { file ->
            val extension = file.extension.lowercase()
            val hash = if (deepSearch && extension in imageExtensions) {
                ImageHashUtils.perceptualHash(file)
            } else {
                file.partialMd5()
            } ?: return@forEach
            hashMap.getOrPut(hash) { mutableListOf() }.add(file)
        }
        return hashMap.values.filter { it.size > 1 }
    }
}
