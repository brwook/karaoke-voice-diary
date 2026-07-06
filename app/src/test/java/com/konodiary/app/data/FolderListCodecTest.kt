package com.konodiary.app.data

import com.konodiary.app.data.sync.decodeFolderList
import com.konodiary.app.data.sync.encodeFolderList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderListCodecTest {

    @Test
    fun `encode then decode round-trips a list preserving order`() {
        val list = listOf(
            "content://tree/A",
            "content://tree/B",
            "content://tree/C",
        )
        assertEquals(list, decodeFolderList(encodeFolderList(list)))
    }

    @Test
    fun `decode of null yields empty list`() {
        assertTrue(decodeFolderList(null).isEmpty())
    }

    @Test
    fun `decode of empty string yields empty list`() {
        assertTrue(decodeFolderList("").isEmpty())
    }

    @Test
    fun `encode of empty list yields empty string`() {
        assertEquals("", encodeFolderList(emptyList()))
    }

    @Test
    fun `single element round-trips`() {
        val list = listOf("content://tree/only")
        assertEquals(list, decodeFolderList(encodeFolderList(list)))
    }

    @Test
    fun `decode drops blank and whitespace-only entries`() {
        // Leading blank, an interior blank line, a whitespace-only line, trailing blank.
        val raw = "\ncontent://tree/A\n\n   \ncontent://tree/B\n"
        assertEquals(
            listOf("content://tree/A", "content://tree/B"),
            decodeFolderList(raw),
        )
    }

    @Test
    fun `decode trims surrounding whitespace on kept entries`() {
        assertEquals(
            listOf("content://tree/A", "content://tree/B"),
            decodeFolderList("  content://tree/A  \n\tcontent://tree/B\t"),
        )
    }
}
