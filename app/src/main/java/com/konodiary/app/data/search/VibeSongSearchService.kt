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
 * [SongSearchService] backed by the Naver VIBE web music API (keyless & free).
 * Prioritized as the first source because it has far better coverage of Korean
 * catalog than the iTunes / Deezer APIs. Uses [HttpURLConnection] on
 * [Dispatchers.IO]; parsing is a pure function ([parseVibeResponse]) so it can
 * be unit-tested without the network.
 */
class VibeSongSearchService : SongSearchService {

    override suspend fun search(query: String, limit: Int): List<SongSearchResult> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://apis.naver.com/vibeWeb/musicapiweb/v3/search/track" +
                    "?query=$q&start=1&display=$limit",
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Vibe search failed: HTTP ${connection.responseCode}")
                }
                val body = connection.inputStream.reader(Charsets.UTF_8).use { it.readText() }
                parseVibeResponse(body)
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * Maps a Naver VIBE search response body to [SongSearchResult]s.
 * Tracks live under `response.result.tracks[]`. Skips entries missing
 * `trackTitle` or with no non-empty artist. Joins multiple `artists[].artistName`
 * with ", ". Album title comes from `album.albumTitle` and artwork from
 * `album.imageUrl` (both optional).
 */
internal fun parseVibeResponse(json: String): List<SongSearchResult> {
    val tracks = JSONObject(json)
        .optJSONObject("response")
        ?.optJSONObject("result")
        ?.optJSONArray("tracks")
        ?: return emptyList()
    val out = ArrayList<SongSearchResult>(tracks.length())
    for (i in 0 until tracks.length()) {
        val item = tracks.optJSONObject(i) ?: continue
        val title = item.optString("trackTitle").takeIf { it.isNotEmpty() } ?: continue
        val artists = item.optJSONArray("artists")
        val artist = buildString {
            if (artists != null) {
                for (j in 0 until artists.length()) {
                    val name = artists.optJSONObject(j)?.optString("artistName")?.takeIf { it.isNotEmpty() }
                        ?: continue
                    if (isNotEmpty()) append(", ")
                    append(name)
                }
            }
        }.takeIf { it.isNotEmpty() } ?: continue
        val album = item.optJSONObject("album")
        val albumTitle = album?.optString("albumTitle")?.takeIf { it.isNotEmpty() }
        val artworkUrl = album?.optString("imageUrl")?.takeIf { it.isNotEmpty() }
        out.add(SongSearchResult(title = title, artist = artist, album = albumTitle, artworkUrl = artworkUrl))
    }
    return out
}
