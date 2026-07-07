package com.konodiary.app.core.contracts

import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.Clip
import com.konodiary.app.core.model.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Decodes an audio file and detects song segments. Implementations must stream
 * the file (never hold full PCM in memory) to support hour-long recordings.
 */
interface AudioAnalyzer {
    /**
     * @param fileUri content:// or file:// uri as a string.
     * @param onProgress called with 0f..1f as decoding proceeds.
     * Runs on a background dispatcher; honors coroutine cancellation.
     */
    suspend fun analyze(fileUri: String, onProgress: (Float) -> Unit): AnalysisResult
}

/**
 * Orchestrates analysis runs and exposes per-recording progress.
 * Requests are queued: ONE runs at a time on battery, up to a few in
 * parallel while the device is plugged in (hour-long decodes are CPU-heavy,
 * and a first bulk import can queue hundreds of files). Recordings waiting
 * in the queue appear in [progress] at 0f until their turn starts.
 */
interface AnalysisController {
    /** recordingId -> progress (0f..1f) for currently running analyses. */
    val progress: StateFlow<Map<Long, Float>>

    /** Start analyzing; a no-op if this recording is already being analyzed. */
    fun startAnalysis(recordingId: Long)

    /**
     * Enqueues every NOT_ANALYZED recording in the DB. The queue itself is
     * in-memory and does not survive process death, so this runs at app start
     * to pick pending work back up.
     */
    fun enqueueAllPending()
}

/** Plays a clipped range of an audio file. Accessed on the main thread. */
interface SegmentPlayer {
    val state: StateFlow<PlayerUiState>

    /** Play [clip]. If endMs <= startMs, plays the whole file without clipping. */
    fun play(clip: Clip)
    fun pause()
    fun resume()
    fun stop()

    /** Seek within the current clip (position relative to the clip start). */
    fun seekTo(positionMs: Long)

    fun release()
}
