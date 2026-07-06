package com.konodiary.app.data

import com.konodiary.app.data.search.parseItunesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItunesResponseParserTest {

    /** Two full songs plus one entry missing artworkUrl100 and collectionName. */
    private val fixture = """
        {
          "resultCount": 3,
          "results": [
            {
              "trackName": "밤편지",
              "artistName": "아이유",
              "collectionName": "Palette",
              "artworkUrl100": "https://is1.mzstatic.com/image/thumb/abc/100x100bb.jpg"
            },
            {
              "trackName": "Spring Day",
              "artistName": "BTS",
              "collectionName": "You Never Walk Alone",
              "artworkUrl100": "https://is5.mzstatic.com/image/thumb/xyz/100x100bb.jpg"
            },
            {
              "trackName": "Live Only Track",
              "artistName": "Some Artist"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `maps track artist album and upgrades artwork to 300x300`() {
        val results = parseItunesResponse(fixture)

        assertEquals(3, results.size)

        val first = results[0]
        assertEquals("밤편지", first.title)
        assertEquals("아이유", first.artist)
        assertEquals("Palette", first.album)
        assertEquals(
            "https://is1.mzstatic.com/image/thumb/abc/300x300bb.jpg",
            first.artworkUrl,
        )

        val second = results[1]
        assertEquals("Spring Day", second.title)
        assertEquals("BTS", second.artist)
        assertEquals("You Never Walk Alone", second.album)
        assertTrue(second.artworkUrl!!.contains("300x300bb"))
    }

    @Test
    fun `entry missing album and artwork yields null fields but is still mapped`() {
        val results = parseItunesResponse(fixture)

        val third = results[2]
        assertEquals("Live Only Track", third.title)
        assertEquals("Some Artist", third.artist)
        assertNull(third.album)
        assertNull(third.artworkUrl)
    }

    @Test
    fun `entries missing trackName or artistName are skipped`() {
        val json = """
            {
              "resultCount": 3,
              "results": [
                { "artistName": "No Title Artist", "collectionName": "X" },
                { "trackName": "No Artist Track", "collectionName": "Y" },
                {
                  "trackName": "Valid Song",
                  "artistName": "Valid Artist",
                  "artworkUrl100": "https://cdn/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()

        val results = parseItunesResponse(json)

        assertEquals(1, results.size)
        assertEquals("Valid Song", results[0].title)
        assertEquals("Valid Artist", results[0].artist)
    }

    @Test
    fun `resultCount zero yields empty list`() {
        val json = """{ "resultCount": 0, "results": [] }"""
        assertTrue(parseItunesResponse(json).isEmpty())
    }
}
