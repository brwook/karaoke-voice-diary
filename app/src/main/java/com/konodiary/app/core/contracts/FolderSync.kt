package com.konodiary.app.core.contracts

import kotlinx.coroutines.flow.StateFlow

data class ScanResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)

/**
 * Connects one or more voice-recorder folders (SAF trees) so recordings can be
 * bulk-imported: a scan walks every connected folder, imports matching audio
 * files that were not imported yet, and queues each one for analysis.
 */
interface FolderSyncManager {
    /** Connected SAF tree URIs in insertion order (persisted across launches). */
    val folders: StateFlow<List<String>>

    /**
     * Persists read permission for [treeUri] and adds it to the connected set.
     * No-op if already connected.
     */
    fun connectFolder(treeUri: String)

    /** Releases the persisted permission for [treeUri] and removes it. */
    fun disconnectFolder(treeUri: String)

    /**
     * Scans the direct children of every connected folder for karaoke
     * recordings (default filter: "음성" name prefix + audio extension),
     * imports the ones whose displayName is not already imported, and starts
     * (queues) analysis for each. A failure in one folder (e.g. revoked
     * permission) must not abort the remaining folders. Returns aggregate
     * counts; zero counts when nothing is connected.
     */
    suspend fun scanAndImport(): ScanResult
}
