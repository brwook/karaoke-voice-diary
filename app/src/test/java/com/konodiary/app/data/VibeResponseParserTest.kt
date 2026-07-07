package com.konodiary.app.data

import com.konodiary.app.data.search.parseVibeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeResponseParserTest {

    /**
     * Trimmed from a real Naver VIBE response
     * (`.../v3/search/track?query=bewhy%20puzzle`). Two full songs — the first
     * with multiple artists to exercise the ", " join — plus one entry missing
     * `trackTitle` (skipped). Public metadata only.
     */
    private val fixture = """
        {
          "response": {
            "result": {
              "originalQuery": "bewhy puzzle",
              "trackTotalCount": 3,
              "tracks": [
                {
                  "trackId": 6247484,
                  "trackTitle": "puzzle",
                  "artists": [
                    { "artistId": 272810, "artistName": "C JAMM" },
                    { "artistId": 275761, "artistName": "비와이(BewhY)" }
                  ],
                  "album": {
                    "albumId": 655811,
                    "albumTitle": "puzzle",
                    "imageUrl": "https://musicmeta-phinf.pstatic.net/album/000/655/655811.jpg?type=r480Fll"
                  }
                },
                {
                  "trackId": 3175583,
                  "trackTitle": "Day Day",
                  "artists": [
                    { "artistId": 275761, "artistName": "비와이(BewhY)" }
                  ],
                  "album": {
                    "albumId": 555111,
                    "albumTitle": "Day Day",
                    "imageUrl": "https://musicmeta-phinf.pstatic.net/album/000/555/555111.jpg?type=r480Fll"
                  }
                },
                {
                  "trackId": 9999999,
                  "artists": [
                    { "artistId": 1, "artistName": "No Title Artist" }
                  ],
                  "album": { "albumId": 1, "albumTitle": "Untitled", "imageUrl": "https://e/x.jpg" }
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `maps title album artwork and joins multiple artists`() {
        val results = parseVibeResponse(fixture)

        // The title-less entry is skipped, leaving two.
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("puzzle", first.title)
        assertEquals("C JAMM, 비와이(BewhY)", first.artist)
        assertEquals("puzzle", first.album)
        assertEquals(
            "https://musicmeta-phinf.pstatic.net/album/000/655/655811.jpg?type=r480Fll",
            first.artworkUrl,
        )

        val second = results[1]
        assertEquals("Day Day", second.title)
        assertEquals("비와이(BewhY)", second.artist)
        assertEquals("Day Day", second.album)
    }

    @Test
    fun `entry missing album yields null album and artwork but is still mapped`() {
        val json = """
            {
              "response": {
                "result": {
                  "tracks": [
                    {
                      "trackTitle": "No Album Song",
                      "artists": [ { "artistName": "Artist" } ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val results = parseVibeResponse(json)

        assertEquals(1, results.size)
        assertEquals("No Album Song", results[0].title)
        assertEquals("Artist", results[0].artist)
        assertNull(results[0].album)
        assertNull(results[0].artworkUrl)
    }

    @Test
    fun `entries missing trackTitle or with no artist name are skipped`() {
        val json = """
            {
              "response": {
                "result": {
                  "tracks": [
                    { "artists": [ { "artistName": "No Title" } ], "album": { "albumTitle": "A" } },
                    { "trackTitle": "No Artists Array", "album": { "albumTitle": "B" } },
                    { "trackTitle": "Empty Artists", "artists": [], "album": { "albumTitle": "C" } },
                    { "trackTitle": "Empty Artist Name", "artists": [ { "artistName": "" } ] },
                    {
                      "trackTitle": "Valid Song",
                      "artists": [ { "artistName": "Valid Artist" } ],
                      "album": { "albumTitle": "Valid Album", "imageUrl": "https://e/v.jpg" }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val results = parseVibeResponse(json)

        assertEquals(1, results.size)
        assertEquals("Valid Song", results[0].title)
        assertEquals("Valid Artist", results[0].artist)
        assertEquals("Valid Album", results[0].album)
    }

    @Test
    fun `empty tracks array yields empty list`() {
        val json = """{ "response": { "result": { "tracks": [] } } }"""
        assertTrue(parseVibeResponse(json).isEmpty())
    }

    @Test
    fun `missing response envelope yields empty list`() {
        assertTrue(parseVibeResponse("""{ }""").isEmpty())
    }
}
