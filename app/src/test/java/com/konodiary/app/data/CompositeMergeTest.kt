package com.konodiary.app.data

import com.konodiary.app.core.model.SongSearchResult
import com.konodiary.app.data.search.mergeResults
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeMergeTest {

    private fun result(title: String, artist: String, artwork: String? = null) =
        SongSearchResult(title = title, artist = artist, album = null, artworkUrl = artwork)

    @Test
    fun `concatenates lists preserving first-seen order`() {
        val a = listOf(result("A", "x"), result("B", "y"))
        val b = listOf(result("C", "z"))

        val merged = mergeResults(a, b, limit = 10)

        assertEquals(listOf("A", "B", "C"), merged.map { it.title })
    }

    @Test
    fun `de-duplicates on trimmed lowercased title and artist`() {
        val first = listOf(result("Puzzle", "비와이", artwork = "keep-me"))
        // Same song via different-case / whitespace-padded fields → dropped.
        val second = listOf(result("  puzzle ", "  비와이  ", artwork = "drop-me"))

        val merged = mergeResults(first, second, limit = 10)

        assertEquals(1, merged.size)
        // The first occurrence is the one retained.
        assertEquals("Puzzle", merged[0].title)
        assertEquals("keep-me", merged[0].artworkUrl)
    }

    @Test
    fun `same title different artist are kept as distinct`() {
        val list = listOf(result("Hello", "Adele"), result("Hello", "Someone Else"))

        val merged = mergeResults(list, limit = 10)

        assertEquals(2, merged.size)
    }

    @Test
    fun `applies limit after merging`() {
        val list = listOf(
            result("A", "1"),
            result("B", "2"),
            result("C", "3"),
            result("D", "4"),
        )

        val merged = mergeResults(list, limit = 2)

        assertEquals(2, merged.size)
        assertEquals(listOf("A", "B"), merged.map { it.title })
    }

    @Test
    fun `de-duplication runs before limit is reached`() {
        // Duplicates in the first list should not consume limit slots twice.
        val first = listOf(result("A", "1"), result("a", "1"))
        val second = listOf(result("B", "2"), result("C", "3"))

        val merged = mergeResults(first, second, limit = 3)

        assertEquals(listOf("A", "B", "C"), merged.map { it.title })
    }

    @Test
    fun `empty lists yield empty result`() {
        assertEquals(emptyList<SongSearchResult>(), mergeResults(emptyList(), emptyList(), limit = 5))
    }
}
