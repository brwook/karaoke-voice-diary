package com.konodiary.app.core.contracts

import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import com.konodiary.app.core.model.Recording
import com.konodiary.app.core.model.Segment
import com.konodiary.app.core.model.Song
import com.konodiary.app.core.model.SongSummary
import com.konodiary.app.core.model.Take
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    fun observeRecordings(): Flow<List<Recording>>
    fun observeRecording(id: Long): Flow<Recording?>
    suspend fun getRecording(id: Long): Recording?

    /** Insert a newly imported file; returns the new recording id. */
    suspend fun importRecording(fileUri: String, displayName: String, durationMs: Long): Long

    suspend fun deleteRecording(id: Long)
    suspend fun setAnalysisState(id: Long, state: AnalysisState)

    /**
     * Transactionally persist an analysis: replace this recording's AUTO segments
     * (MANUAL kept), upsert the envelope, update duration + set state = ANALYZED.
     */
    suspend fun saveAnalysisResult(result: AnalysisResult)

    fun observeEnvelope(recordingId: Long): Flow<Envelope?>

    /**
     * Recovers recordings left stuck at ANALYZING by a process kill mid-analysis,
     * resetting them to NOT_ANALYZED. Called once at app start.
     */
    suspend fun resetStaleAnalyzing()
}

interface SegmentRepository {
    fun observeSegments(recordingId: Long): Flow<List<Segment>>
    suspend fun getSegment(id: Long): Segment?

    /** Add a user-defined MANUAL segment; returns the new id. */
    suspend fun addManualSegment(recordingId: Long, startMs: Long, endMs: Long): Long

    suspend fun updateBounds(id: Long, startMs: Long, endMs: Long)
    suspend fun setRating(id: Long, rating: Int)
    suspend fun assignSong(id: Long, songId: Long?)
    suspend fun setMemo(id: Long, memo: String)
    suspend fun deleteSegment(id: Long)
}

interface SongRepository {
    fun observeSongs(): Flow<List<Song>>

    /** Songs with take-count + best rating, sorted best-rating desc then title. */
    fun observeSongsWithTakes(): Flow<List<SongSummary>>

    /** All takes of a song, enriched with recording info, best rating first. */
    fun observeTakesForSong(songId: Long): Flow<List<Take>>

    suspend fun getSong(id: Long): Song?
    suspend fun createSong(title: String, artist: String, artworkUrl: String? = null): Long

    /**
     * Returns the id of an existing song matching title+artist (trimmed,
     * case-insensitive) or creates one. If the existing song has no artwork and
     * [artworkUrl] is given, the artwork is filled in.
     */
    suspend fun findOrCreateSong(title: String, artist: String, artworkUrl: String? = null): Long

    suspend fun deleteSong(id: Long)
}
