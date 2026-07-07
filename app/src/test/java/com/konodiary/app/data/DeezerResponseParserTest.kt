package com.konodiary.app.data

import com.konodiary.app.data.search.parseDeezerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerResponseParserTest {

    /**
     * Two full songs plus one entry missing title (skipped).
     * The second song has no cover_medium, exercising the fallback to `cover`.
     */
    private val fixture = """
        {
          "data": [
            {
              "title": "Puzzle",
              "artist": { "name": "비와이" },
              "album": {
                "title": "The Genesis",
                "cover": "https://e-cdn/cover.jpg",
                "cover_medium": "https://e-cdn/cover_medium.jpg"
              }
            },
            {
              "title": "Fallback Cover",
              "artist": { "name": "Some Artist" },
              "album": {
                "title": "Fallback Album",
                "cover": "https://e-cdn/only_cover.jpg"
              }
            },
            {
              "artist": { "name": "No Title Artist" },
              "album": { "title": "Untitled", "cover_medium": "https://e-cdn/x.jpg" }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `maps title artist album and prefers cover_medium`() {
        val results = parseDeezerResponse(fixture)

        // The title-less entry is skipped, leaving two.
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("Puzzle", first.title)
        assertEquals("비와이", first.artist)
        assertEquals("The Genesis", first.album)
        assertEquals("https://e-cdn/cover_medium.jpg", first.artworkUrl)
    }

    @Test
    fun `falls back to cover when cover_medium missing`() {
        val results = parseDeezerResponse(fixture)

        val second = results[1]
        assertEquals("Fallback Cover", second.title)
        assertEquals("Some Artist", second.artist)
        assertEquals("Fallback Album", second.album)
        assertEquals("https://e-cdn/only_cover.jpg", second.artworkUrl)
    }

    @Test
    fun `entries missing title or artist name are skipped`() {
        val json = """
            {
              "data": [
                { "artist": { "name": "No Title" }, "album": { "title": "A" } },
                { "title": "No Artist Object", "album": { "title": "B" } },
                { "title": "Empty Artist Name", "artist": { "name": "" }, "album": { "title": "C" } },
                {
                  "title": "Valid Song",
                  "artist": { "name": "Valid Artist" },
                  "album": { "title": "Valid Album", "cover_medium": "https://e-cdn/v.jpg" }
                }
              ]
            }
        """.trimIndent()

        val results = parseDeezerResponse(json)

        assertEquals(1, results.size)
        assertEquals("Valid Song", results[0].title)
        assertEquals("Valid Artist", results[0].artist)
    }

    @Test
    fun `entry without album yields null album and artwork`() {
        val json = """
            {
              "data": [
                { "title": "No Album Song", "artist": { "name": "Artist" } }
              ]
            }
        """.trimIndent()

        val results = parseDeezerResponse(json)

        assertEquals(1, results.size)
        assertEquals("No Album Song", results[0].title)
        assertNull(results[0].album)
        assertNull(results[0].artworkUrl)
    }

    @Test
    fun `empty data array yields empty list`() {
        assertTrue(parseDeezerResponse("""{ "data": [] }""").isEmpty())
    }
}
