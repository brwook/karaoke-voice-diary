package com.konodiary.app.core.model

/**
 * Domain models shared across all layers. These are the CONTRACT — plain,
 * android-free data holders. Data/audio/player/ui layers map to/from these.
 */

/** Lifecycle of a recording's segment analysis. Persisted as its `name` string. */
enum class AnalysisState {
    NOT_ANALYZED,
    ANALYZING,
    ANALYZED,
    FAILED,
}

/** Where a segment came from. Persisted as its `name` string. */
enum class SegmentSource {
    AUTO,
    MANUAL,
}

/** An imported karaoke session audio file. */
data class Recording(
    val id: Long,
    val fileUri: String,
    val displayName: String,
    val durationMs: Long,
    val importedAt: Long,
    val analysisState: AnalysisState,
)

/** A song the user registers and attaches takes to. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
)

/** One take: a time range inside a recording, optionally tied to a song and rated. */
data class Segment(
    val id: Long,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val songId: Long?,
    val rating: Int,          // 0 = unrated, 1..5 = stars
    val memo: String,
    val source: SegmentSource,
    val confidence: Float,    // 0f..1f, share of active frames in the range
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/**
 * A candidate song range produced by the segmenter (before it becomes a
 * persisted [Segment]). Times are in milliseconds from the start of the file.
 */
data class DetectedSegment(
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
)

/**
 * Loudness envelope for a recording, used to draw the waveform.
 * [data] holds one normalized value (0f..1f) per [frameMs] window.
 */
class Envelope(
    val recordingId: Long,
    val frameMs: Int,
    val data: FloatArray,
)

/** Result of analyzing a recording, handed to the repository transactionally. */
data class AnalysisResult(
    val recordingId: Long,
    val durationMs: Long,
    val segments: List<DetectedSegment>,
    val envelope: Envelope,
)

/** A song plus aggregate stats, for the songs list. */
data class SongSummary(
    val song: Song,
    val takeCount: Int,
    val bestRating: Int,
)

/** A single take of a song, enriched with its source recording, for song detail. */
data class Take(
    val segment: Segment,
    val recordingId: Long,
    val recordingName: String,
    val recordingFileUri: String,
    val recordingImportedAt: Long,
)

/** A clip request for the player: a range inside a media file. */
data class Clip(
    val uri: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

/** Observable player state. Positions are relative to the current clip. */
data class PlayerUiState(
    val clip: Clip? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Non-null when the last playback attempt failed (e.g. lost URI permission). */
    val error: String? = null,
)
