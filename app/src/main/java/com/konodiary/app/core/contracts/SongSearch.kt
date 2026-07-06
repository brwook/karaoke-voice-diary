package com.konodiary.app.core.contracts

import com.konodiary.app.core.model.SongSearchResult

/**
 * Online song-catalog search (iTunes Search API — keyless & free).
 * Runs its own IO dispatching; throws on network/HTTP failure so the UI can
 * show an error state with the manual-entry fallback.
 */
interface SongSearchService {
    suspend fun search(query: String, limit: Int = 20): List<SongSearchResult>
}
