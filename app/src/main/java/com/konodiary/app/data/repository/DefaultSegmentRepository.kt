package com.konodiary.app.data.repository

import com.konodiary.app.core.contracts.SegmentRepository
import com.konodiary.app.core.model.RecordingSegmentCounts
import com.konodiary.app.core.model.Segment
import com.konodiary.app.data.db.KonoDatabase
import com.konodiary.app.data.db.SegmentEntity
import com.konodiary.app.data.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSegmentRepository(private val db: KonoDatabase) : SegmentRepository {

    private val segmentDao = db.segmentDao()

    override fun observeSegments(recordingId: Long): Flow<List<Segment>> =
        segmentDao.observeForRecording(recordingId).map { list -> list.map { it.toDomain() } }

    override fun observeCountsByRecording(): Flow<Map<Long, RecordingSegmentCounts>> =
        segmentDao.observeCountsByRecording().map { rows ->
            rows.associate { row ->
                row.recordingId to RecordingSegmentCounts(
                    recordingId = row.recordingId,
                    total = row.total,
                    registered = row.registered,
                )
            }
        }

    override suspend fun getSegment(id: Long): Segment? =
        segmentDao.getById(id)?.toDomain()

    override suspend fun addManualSegment(
        recordingId: Long,
        startMs: Long,
        endMs: Long,
    ): Long = segmentDao.insert(
        SegmentEntity(
            recordingId = recordingId,
            startMs = startMs,
            endMs = endMs,
            songId = null,
            rating = 0,
            memo = "",
            source = "MANUAL",
            confidence = 1f,
        )
    )

    override suspend fun updateBounds(id: Long, startMs: Long, endMs: Long) =
        segmentDao.updateBounds(id, startMs, endMs)

    override suspend fun setRating(id: Long, rating: Int) =
        segmentDao.setRating(id, rating.coerceIn(0, 5))

    override suspend fun assignSong(id: Long, songId: Long?) =
        segmentDao.assignSong(id, songId)

    override suspend fun setMemo(id: Long, memo: String) =
        segmentDao.setMemo(id, memo)

    override suspend fun deleteSegment(id: Long) =
        segmentDao.deleteById(id)
}
