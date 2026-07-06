package com.konodiary.app.data

import com.konodiary.app.data.sync.isKaraokeRecordingName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeRecordingNameFilterTest {

    @Test
    fun `voice-prefixed m4a is accepted`() {
        assertTrue(isKaraokeRecordingName("음성 260703_193446.m4a"))
    }

    @Test
    fun `voice-prefixed 3ga is accepted`() {
        assertTrue(isKaraokeRecordingName("음성 135.3ga"))
    }

    @Test
    fun `uppercase extension is accepted`() {
        assertTrue(isKaraokeRecordingName("음성 1.M4A"))
    }

    @Test
    fun `call recording without voice prefix is rejected`() {
        assertFalse(isKaraokeRecordingName("-김영안교수님_010..._2025.m4a"))
    }

    @Test
    fun `voice-prefixed non-audio extension is rejected`() {
        assertFalse(isKaraokeRecordingName("음성 노트.txt"))
    }

    @Test
    fun `audio file without voice prefix is rejected`() {
        assertFalse(isKaraokeRecordingName("recording.m4a"))
    }
}
