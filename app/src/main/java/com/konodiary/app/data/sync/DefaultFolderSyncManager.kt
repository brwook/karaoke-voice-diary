package com.konodiary.app.data.sync

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.FolderSyncManager
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.contracts.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Audio extensions accepted from the connected folder (lowercase, no dot). */
private val AUDIO_EXTENSIONS = setOf("m4a", "3ga", "3gp", "aac", "mp3", "wav", "ogg")

/**
 * Pure filename filter: a file is a karaoke recording iff its (trimmed) name
 * starts with "음성" AND its extension is a known audio type. Keeps call
 * recordings ("-김OO교수님_...") and other folder junk out of the import.
 */
internal fun isKaraokeRecordingName(name: String): Boolean {
    val trimmed = name.trim()
    if (!trimmed.startsWith("음성")) return false
    val ext = trimmed.substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

/** Serialize the folder list to newline-separated storage form (insertion order kept). */
internal fun encodeFolderList(list: List<String>): String = list.joinToString("\n")

/**
 * Parse the newline-separated storage form back into a list, dropping blank /
 * whitespace-only entries. Null or empty input yields an empty list.
 */
internal fun decodeFolderList(raw: String?): List<String> =
    raw?.split("\n").orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

class DefaultFolderSyncManager(
    private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val analysisController: AnalysisController,
) : FolderSyncManager {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _folders = MutableStateFlow(loadInitialFolders())
    override val folders: StateFlow<List<String>> = _folders.asStateFlow()

    /**
     * Loads the persisted list, one-time migrating the legacy single-folder key
     * into it (then removing the old key) so existing installs keep their folder.
     */
    private fun loadInitialFolders(): List<String> {
        val current = decodeFolderList(prefs.getString(KEY_FOLDER_URIS, null)).toMutableList()

        val legacy = prefs.getString(KEY_LEGACY_FOLDER_URI, null)?.trim()
        if (!legacy.isNullOrEmpty()) {
            if (legacy !in current) current.add(legacy)
            prefs.edit()
                .putString(KEY_FOLDER_URIS, encodeFolderList(current))
                .remove(KEY_LEGACY_FOLDER_URI)
                .apply()
        }

        return current
    }

    private fun persist(list: List<String>) {
        prefs.edit().putString(KEY_FOLDER_URIS, encodeFolderList(list)).apply()
        _folders.value = list
    }

    override fun connectFolder(treeUri: String) {
        if (treeUri in _folders.value) return

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        persist(_folders.value + treeUri)
    }

    override fun disconnectFolder(treeUri: String) {
        if (treeUri !in _folders.value) return

        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        persist(_folders.value - treeUri)
    }

    override suspend fun scanAndImport(): ScanResult = withContext(Dispatchers.IO) {
        val treeUris = _folders.value
        if (treeUris.isEmpty()) return@withContext ScanResult(0, 0, 0)

        // Names already in the DB, plus names imported earlier in *this* scan so a
        // file with the same name in two folders is imported only once.
        val seenNames = recordingRepository.observeRecordings().first()
            .map { it.displayName }
            .toMutableSet()

        var imported = 0
        var skipped = 0
        var failed = 0

        for (treeUri in treeUris) {
            // Isolate per-folder failures (DocumentFile null, revoked permission,
            // exceptions) so one bad folder does not abort the rest.
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@runCatching

                for (file in tree.listFiles()) {
                    if (!file.isFile) continue
                    val name = file.name ?: continue
                    if (!isKaraokeRecordingName(name)) continue

                    if (name in seenNames) {
                        skipped++
                        continue
                    }

                    val durationMs = extractDurationMs(file.uri)
                    if (durationMs == null) {
                        failed++
                        continue
                    }

                    val newId =
                        recordingRepository.importRecording(file.uri.toString(), name, durationMs)
                    analysisController.startAnalysis(newId)
                    seenNames.add(name)
                    imported++
                }
            }
        }

        ScanResult(imported, skipped, failed)
    }

    /** Reads track duration; returns null (caller counts as failed) on any error. */
    private fun extractDurationMs(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val PREFS_NAME = "kono_prefs"
        const val KEY_FOLDER_URIS = "folder_tree_uris"
        const val KEY_LEGACY_FOLDER_URI = "folder_tree_uri"
    }
}
