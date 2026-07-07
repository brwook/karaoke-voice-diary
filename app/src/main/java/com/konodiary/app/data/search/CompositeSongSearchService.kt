package com.konodiary.app.data.search

import com.konodiary.app.core.contracts.SongSearchService
import com.konodiary.app.core.model.SongSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * [SongSearchService] that fans a single query out to multiple catalogs in
 * parallel, then merges + de-duplicates the results. Widens coverage past any
 * single catalog's gaps (e.g. Korean songs missing from iTunes/Deezer but
 * present on Naver VIBE) and strengthens mixed-language queries via an extra
 * title-only iTunes pass.
 *
 * Each pass is isolated with [runCatching] so one source failing (offline, HTTP
 * error) still yields the others. Only when *all* passes fail does the last
 * exception propagate, so the UI keeps its error/fallback state.
 */
class CompositeSongSearchService(
    private val vibe: VibeSongSearchService,
    private val itunes: ItunesSongSearchService,
    private val deezer: DeezerSongSearchService,
) : SongSearchService {

    override suspend fun search(query: String, limit: Int): List<SongSearchResult> = coroutineScope {
        val vibeSearch = async { runCatching { vibe.search(query, limit) } }
        val itunesCombined = async { runCatching { itunes.searchWith(query, limit, attribute = null) } }
        val itunesSongTerm = async { runCatching { itunes.searchWith(query, limit, attribute = "songTerm") } }
        val deezerCombined = async { runCatching { deezer.search(query, limit) } }

        val vibeResult = vibeSearch.await()
        val combinedResult = itunesCombined.await()
        val songTermResult = itunesSongTerm.await()
        val deezerResult = deezerCombined.await()

        // All passes failed → surface the error (keep UI error/fallback state).
        if (vibeResult.isFailure &&
            combinedResult.isFailure &&
            songTermResult.isFailure &&
            deezerResult.isFailure
        ) {
            throw deezerResult.exceptionOrNull()
                ?: songTermResult.exceptionOrNull()
                ?: combinedResult.exceptionOrNull()
                ?: vibeResult.exceptionOrNull()!!
        }

        // Merge order: VIBE → iTunes combined → Deezer → iTunes songTerm
        // (Korean results first).
        mergeResults(
            vibeResult.getOrDefault(emptyList()),
            combinedResult.getOrDefault(emptyList()),
            deezerResult.getOrDefault(emptyList()),
            songTermResult.getOrDefault(emptyList()),
            limit = limit,
        )
    }
}

/**
 * Concatenates [lists] in the given order, drops later duplicates keyed by
 * trimmed + lowercased (title, artist), preserves first-seen order, and caps at
 * [limit]. Pure and network-free for unit testing.
 */
internal fun mergeResults(vararg lists: List<SongSearchResult>, limit: Int): List<SongSearchResult> {
    val seen = HashSet<Pair<String, String>>()
    val out = ArrayList<SongSearchResult>()
    for (list in lists) {
        for (r in list) {
            if (out.size >= limit) return out
            val key = r.title.trim().lowercase() to r.artist.trim().lowercase()
            if (seen.add(key)) out.add(r)
        }
    }
    return out
}
