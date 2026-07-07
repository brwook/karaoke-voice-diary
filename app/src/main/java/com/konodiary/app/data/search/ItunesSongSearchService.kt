package com.konodiary.app.data.search

import com.konodiary.app.core.contracts.SongSearchService
import com.konodiary.app.core.model.SongSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [SongSearchService] backed by the iTunes Search API (keyless & free).
 * Uses [HttpURLConnection] on [Dispatchers.IO]; parsing is a pure function
 * ([parseItunesResponse]) so it can be unit-tested without the network.
 */
class ItunesSongSearchService : SongSearchService {

    override suspend fun search(query: String, limit: Int): List<SongSearchResult> =
        searchWith(query, limit, attribute = null)

    /**
     * Runs a single iTunes search pass. When [attribute] is non-null (e.g.
     * "songTerm") it narrows matching to that field, which is more forgiving for
     * mixed-language / title-only queries than the default combined term search.
     */
    suspend fun searchWith(query: String, limit: Int, attribute: String?): List<SongSearchResult> =
        withContext(Dispatchers.IO) {
            val term = URLEncoder.encode(query, "UTF-8")
            val attributeParam = attribute?.let { "&attribute=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val url = URL(
                "https://itunes.apple.com/search" +
                    "?media=music&entity=song&country=KR&limit=$limit&term=$term$attributeParam",
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("iTunes search failed: HTTP ${connection.responseCode}")
                }
                val body = connection.inputStream.reader(Charsets.UTF_8).use { it.readText() }
                parseItunesResponse(body)
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * Maps an iTunes Search API response body to [SongSearchResult]s.
 * Skips entries missing trackName or artistName. Requests the larger 300x300
 * artwork by rewriting the 100x100 URL the API returns.
 */
internal fun parseItunesResponse(json: String): List<SongSearchResult> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    val out = ArrayList<SongSearchResult>(results.length())
    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue
        val title = item.optString("trackName").takeIf { it.isNotEmpty() } ?: continue
        val artist = item.optString("artistName").takeIf { it.isNotEmpty() } ?: continue
        val album = item.optString("collectionName").takeIf { it.isNotEmpty() }
        val artworkUrl = item.optString("artworkUrl100").takeIf { it.isNotEmpty() }
            ?.replace("100x100bb", "300x300bb")
        out.add(SongSearchResult(title = title, artist = artist, album = album, artworkUrl = artworkUrl))
    }
    return out
}
