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
 * [SongSearchService] backed by the Deezer public search API (keyless & free).
 * Fills catalog gaps the iTunes API misses. Uses [HttpURLConnection] on
 * [Dispatchers.IO]; parsing is a pure function ([parseDeezerResponse]) so it can
 * be unit-tested without the network.
 */
class DeezerSongSearchService : SongSearchService {

    override suspend fun search(query: String, limit: Int): List<SongSearchResult> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.deezer.com/search?q=$q&limit=$limit")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Deezer search failed: HTTP ${connection.responseCode}")
                }
                val body = connection.inputStream.reader(Charsets.UTF_8).use { it.readText() }
                parseDeezerResponse(body)
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * Maps a Deezer search response body to [SongSearchResult]s.
 * Skips entries missing title or artist name. Prefers `album.cover_medium`,
 * falling back to `album.cover` for artwork.
 */
internal fun parseDeezerResponse(json: String): List<SongSearchResult> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    val out = ArrayList<SongSearchResult>(data.length())
    for (i in 0 until data.length()) {
        val item = data.optJSONObject(i) ?: continue
        val title = item.optString("title").takeIf { it.isNotEmpty() } ?: continue
        val artist = item.optJSONObject("artist")
            ?.optString("name")?.takeIf { it.isNotEmpty() } ?: continue
        val album = item.optJSONObject("album")
        val albumTitle = album?.optString("title")?.takeIf { it.isNotEmpty() }
        val artworkUrl = album?.let { a ->
            a.optString("cover_medium").takeIf { it.isNotEmpty() }
                ?: a.optString("cover").takeIf { it.isNotEmpty() }
        }
        out.add(SongSearchResult(title = title, artist = artist, album = albumTitle, artworkUrl = artworkUrl))
    }
    return out
}
