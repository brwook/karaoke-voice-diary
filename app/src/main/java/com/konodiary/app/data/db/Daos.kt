package com.konodiary.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Insert
    suspend fun insert(entity: RecordingEntity): Long

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recordings SET analysisState = :state WHERE id = :id")
    suspend fun setAnalysisState(id: Long, state: String)

    @Query("UPDATE recordings SET durationMs = :durationMs, analysisState = :state WHERE id = :id")
    suspend fun setDurationAndState(id: Long, durationMs: Long, state: String)

    /**
     * Analysis runs in an in-memory scope, so a process kill mid-analysis leaves
     * rows stuck at ANALYZING forever (the UI shows an eternal spinner with no
     * retry). Called once at app start to make them analyzable again.
     */
    @Query("UPDATE recordings SET analysisState = 'NOT_ANALYZED' WHERE analysisState = 'ANALYZING'")
    suspend fun resetStaleAnalyzing()
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs ASC")
    fun observeForRecording(recordingId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun getById(id: Long): SegmentEntity?

    @Insert
    suspend fun insert(entity: SegmentEntity): Long

    @Query("DELETE FROM segments WHERE recordingId = :recordingId AND source = 'AUTO'")
    suspend fun deleteAutoForRecording(recordingId: Long)

    @Query("DELETE FROM segments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE segments SET startMs = :startMs, endMs = :endMs WHERE id = :id")
    suspend fun updateBounds(id: Long, startMs: Long, endMs: Long)

    @Query("UPDATE segments SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("UPDATE segments SET songId = :songId WHERE id = :id")
    suspend fun assignSong(id: Long, songId: Long?)

    @Query("UPDATE segments SET memo = :memo WHERE id = :id")
    suspend fun setMemo(id: Long, memo: String)
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    /** NOCASE ignores English case; Korean is unaffected. TRIM ignores stray whitespace. */
    @Query(
        """
        SELECT * FROM songs
        WHERE TRIM(title) = TRIM(:title) COLLATE NOCASE
          AND TRIM(artist) = TRIM(:artist) COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun findByTitleArtist(title: String, artist: String): SongEntity?

    @Insert
    suspend fun insert(entity: SongEntity): Long

    @Query("UPDATE songs SET artworkUrl = :artworkUrl WHERE id = :id")
    suspend fun updateArtwork(id: Long, artworkUrl: String?)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT s.id AS id, s.title AS title, s.artist AS artist, s.artworkUrl AS artworkUrl,
               COUNT(seg.id) AS takeCount,
               COALESCE(MAX(seg.rating), 0) AS bestRating
        FROM songs s
        LEFT JOIN segments seg ON seg.songId = s.id
        GROUP BY s.id
        ORDER BY bestRating DESC, s.title ASC
        """
    )
    fun observeSongsWithTakes(): Flow<List<SongSummaryRow>>

    @Query(
        """
        SELECT seg.*, r.displayName AS recordingName, r.fileUri AS recordingFileUri,
               r.importedAt AS recordingImportedAt
        FROM segments seg
        JOIN recordings r ON r.id = seg.recordingId
        WHERE seg.songId = :songId
        ORDER BY seg.rating DESC, seg.startMs ASC
        """
    )
    fun observeTakesForSong(songId: Long): Flow<List<TakeRow>>
}

@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelopes WHERE recordingId = :recordingId")
    fun observeForRecording(recordingId: Long): Flow<EnvelopeEntity?>

    @Upsert
    suspend fun upsert(entity: EnvelopeEntity)
}
