package com.konodiary.app.audio

import com.konodiary.app.audio.analysis.KaraokeSegmenter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeSegmenterTest {

    private val frameMs = 100
    private val quietDb = -60f
    private val loudDb = -12f

    /** Builds a dB curve from (durationSeconds, loud?) segments. */
    private fun buildCurve(vararg parts: Pair<Int, Boolean>): FloatArray {
        val out = ArrayList<Float>()
        for ((seconds, loud) in parts) {
            val frames = seconds * 1000 / frameMs
            repeat(frames) { out.add(if (loud) loudDb else quietDb) }
        }
        return out.toFloatArray()
    }

    @Test
    fun `detects two songs separated by silence`() {
        val curve = buildCurve(
            60 to false,   // intro silence
            180 to true,   // song 1
            40 to false,   // gap / song select
            240 to true,   // song 2
            30 to false,   // outro silence
        )

        val segments = KaraokeSegmenter.segment(curve, frameMs)

        assertEquals(2, segments.size)

        val (s1, s2) = segments
        // song 1 ~ 60s..240s (with -1s / +1.5s padding)
        assertTrue("s1.start=${s1.startMs}", s1.startMs in 57_000..62_000)
        assertTrue("s1.end=${s1.endMs}", s1.endMs in 239_000..243_000)
        // song 2 ~ 280s..520s
        assertTrue("s2.start=${s2.startMs}", s2.startMs in 277_000..282_000)
        assertTrue("s2.end=${s2.endMs}", s2.endMs in 519_000..523_000)

        assertTrue(s1.confidence > 0.8f)
        assertTrue(s2.confidence > 0.8f)
    }

    @Test
    fun `fills short interlude gaps within a song`() {
        // A single song with an 8s quiet bridge in the middle stays one segment.
        val curve = buildCurve(
            60 to false,
            90 to true,
            8 to false,    // bridge (<= 12s -> filled)
            90 to true,
            30 to false,
        )

        val segments = KaraokeSegmenter.segment(curve, frameMs)

        assertEquals(1, segments.size)
    }

    @Test
    fun `drops runs that are too short to be a song`() {
        // A lone 30s loud blip (< 50s) is discarded.
        val curve = buildCurve(
            60 to false,
            30 to true,
            60 to false,
        )

        val segments = KaraokeSegmenter.segment(curve, frameMs)

        assertTrue(segments.isEmpty())
    }

    @Test
    fun `uniform input returns a single whole-file segment`() {
        val curve = buildCurve(300 to true) // 5 min, all loud -> uniform

        val segments = KaraokeSegmenter.segment(curve, frameMs)

        assertEquals(1, segments.size)
        assertEquals(0L, segments[0].startMs)
        assertEquals(300_000L, segments[0].endMs)
    }

    @Test
    fun `empty input yields no segments`() {
        assertTrue(KaraokeSegmenter.segment(FloatArray(0), frameMs).isEmpty())
    }

    @Test
    fun `movingAverage smooths a per-frame spike`() {
        // The waveform envelope now reuses this same smoothing (SPEC §5.5), so a
        // single-frame spike must be attenuated by the centered window.
        val n = 100
        val input = FloatArray(n) { 0f }
        input[50] = 10f
        val window = 2000 / frameMs // 20 frames, matching the analyzer's envelope window

        val out = KaraokeSegmenter.movingAverage(input, window)

        assertEquals(n, out.size)
        // Spike is spread over the window: its own frame drops well below the raw 10f...
        assertTrue("peak=${out[50]}", out[50] in 0.1f..1.0f)
        // ...and neighbors within the window pick up a non-zero share.
        assertTrue("neighbor=${out[45]}", out[45] > 0f)
        // The overall average is conserved (spike mass / n).
        val expectedMean = input.sum() / n
        assertEquals(expectedMean, out.sum() / n, 1e-3f)
    }

    @Test
    fun `movingAverage with window of one is identity`() {
        val input = floatArrayOf(-60f, -12f, -30f, -5f)
        val out = KaraokeSegmenter.movingAverage(input, 1)
        assertArrayEquals(input, out, 0f)
    }
}
