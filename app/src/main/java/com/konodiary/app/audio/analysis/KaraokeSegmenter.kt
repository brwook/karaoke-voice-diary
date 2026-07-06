package com.konodiary.app.audio.analysis

import com.konodiary.app.core.model.DetectedSegment
import kotlin.math.roundToInt

/**
 * Pure, android-free song-segmentation logic. Given a per-frame loudness curve
 * (dBFS), returns the ranges that look like sung songs (as opposed to silence,
 * banter, or song-selection time). Kept side-effect free so it can be unit
 * tested on the JVM. See docs/SPEC.md §5.
 */
object KaraokeSegmenter {

    data class Params(
        val smoothingWindowMs: Int = 2_000,
        val noiseFloorPercentile: Double = 0.20,
        val musicPercentile: Double = 0.95,
        val thresholdFraction: Double = 0.35,
        /** If (p95 - noiseFloor) is below this, the file is treated as uniform. */
        val uniformRangeDb: Double = 6.0,
        /** Inactive gaps up to this long are filled (song interludes/bridges). */
        val maxGapFillMs: Int = 12_000,
        /** Active runs shorter than this are discarded (banter, song select). */
        val minSegmentMs: Int = 50_000,
        val padBeforeMs: Int = 1_000,
        val padAfterMs: Int = 1_500,
    )

    /**
     * @param frameDb one dBFS value per frame (more negative = quieter).
     * @param frameMs milliseconds each frame represents.
     */
    fun segment(
        frameDb: FloatArray,
        frameMs: Int,
        params: Params = Params(),
    ): List<DetectedSegment> {
        val n = frameDb.size
        if (n == 0 || frameMs <= 0) return emptyList()
        val totalMs = n.toLong() * frameMs

        val smoothed = movingAverage(frameDb, (params.smoothingWindowMs / frameMs).coerceAtLeast(1))

        val noiseFloor = percentile(smoothed, params.noiseFloorPercentile)
        val p95 = percentile(smoothed, params.musicPercentile)

        // Uniform loudness (e.g. entirely quiet or entirely loud) -> one segment.
        if (p95 - noiseFloor < params.uniformRangeDb) {
            return listOf(DetectedSegment(0L, totalMs, 1f))
        }

        val threshold = noiseFloor + params.thresholdFraction * (p95 - noiseFloor)

        // Original per-frame activity (used later for confidence).
        val active = BooleanArray(n) { smoothed[it] > threshold }

        // Morphology 1: fill short inactive gaps between active frames.
        val filled = active.copyOf()
        val maxGapFrames = params.maxGapFillMs / frameMs
        var i = 0
        // find first active
        while (i < n && !filled[i]) i++
        while (i < n) {
            // i is active; find next active after a gap
            var j = i + 1
            while (j < n && !filled[j]) j++
            if (j < n) {
                val gap = j - i - 1
                if (gap in 1..maxGapFrames) {
                    for (k in (i + 1) until j) filled[k] = true
                }
                i = j
            } else {
                break
            }
        }

        // Collect active runs from the filled array.
        val minSegFrames = params.minSegmentMs / frameMs
        val runs = ArrayList<IntRange>()
        var start = -1
        for (idx in 0 until n) {
            if (filled[idx] && start == -1) {
                start = idx
            } else if (!filled[idx] && start != -1) {
                runs.add(start until idx)
                start = -1
            }
        }
        if (start != -1) runs.add(start until n)

        // Morphology 2: drop runs that are too short to be a song.
        val kept = runs.filter { it.last - it.first + 1 >= minSegFrames }

        // Convert to padded, non-overlapping ms ranges + confidence.
        val result = ArrayList<DetectedSegment>(kept.size)
        var prevEndMs = 0L
        for (run in kept) {
            val runStartMs = run.first.toLong() * frameMs
            val runEndMs = (run.last + 1).toLong() * frameMs

            val startMs = (runStartMs - params.padBeforeMs)
                .coerceAtLeast(prevEndMs)
                .coerceAtLeast(0L)
            val endMs = (runEndMs + params.padAfterMs).coerceAtMost(totalMs)

            if (endMs <= startMs) continue

            var activeCount = 0
            for (idx in run) if (active[idx]) activeCount++
            val confidence = activeCount.toFloat() / (run.last - run.first + 1)

            result.add(DetectedSegment(startMs, endMs, confidence))
            prevEndMs = endMs
        }
        return result
    }

    /**
     * Centered moving average over `window` frames. Exposed (internal) so the
     * analyzer can build the waveform envelope from the same smoothed curve the
     * segmenter uses (see docs/SPEC.md §5.5).
     */
    internal fun movingAverage(input: FloatArray, window: Int): FloatArray {
        val n = input.size
        if (window <= 1 || n == 0) return input.copyOf()
        val out = FloatArray(n)
        val half = window / 2
        // Prefix sums for O(n).
        val prefix = DoubleArray(n + 1)
        for (idx in 0 until n) prefix[idx + 1] = prefix[idx] + input[idx]
        for (idx in 0 until n) {
            val lo = (idx - half).coerceAtLeast(0)
            val hi = (idx + half).coerceAtMost(n - 1)
            val sum = prefix[hi + 1] - prefix[lo]
            out[idx] = (sum / (hi - lo + 1)).toFloat()
        }
        return out
    }

    /** Nearest-rank percentile (p in 0.0..1.0) over a copy of [values]. */
    private fun percentile(values: FloatArray, p: Double): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf()
        sorted.sort()
        val idx = (p * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
