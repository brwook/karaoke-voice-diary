package com.konodiary.app.data.repository

import androidx.room.withTransaction
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import com.konodiary.app.core.model.Recording
import com.konodiary.app.data.db.KonoDatabase
import com.konodiary.app.data.db.RecordingEntity
import com.konodiary.app.data.db.SegmentEntity
import com.konodiary.app.data.mapper.toDomain
import com.konodiary.app.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultRecordingRepository(private val db: KonoDatabase) : RecordingRepository {

    private val recordingDao = db.recordingDao()
    private val segmentDao = db.segmentDao()
    private val envelopeDao = db.envelopeDao()

    override fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecording(id: Long): Flow<Recording?> =
        recordingDao.observeById(id).map { it?.toDomain() }

    override suspend fun getRecording(id: Long): Recording? =
        recordingDao.getById(id)?.toDomain()

    override suspend fun importRecording(
        fileUri: String,
        displayName: String,
        durationMs: Long,
    ): Long = recordingDao.insert(
        RecordingEntity(
            fileUri = fileUri,
            displayName = displayName,
            durationMs = durationMs,
            importedAt = System.currentTimeMillis(),
            analysisState = AnalysisState.NOT_ANALYZED.name,
        )
    )

    override suspend fun deleteRecording(id: Long) = recordingDao.deleteById(id)

    override suspend fun setAnalysisState(id: Long, state: AnalysisState) =
        recordingDao.setAnalysisState(id, state.name)

    override suspend fun saveAnalysisResult(result: AnalysisResult) {
        db.withTransaction {
            segmentDao.deleteAutoForRecording(result.recordingId)
            result.segments.forEach { seg ->
                segmentDao.insert(
                    SegmentEntity(
                        recordingId = result.recordingId,
                        startMs = seg.startMs,
                        endMs = seg.endMs,
                        songId = null,
                        rating = 0,
                        memo = "",
                        source = "AUTO",
                        confidence = seg.confidence,
                    )
                )
            }
            envelopeDao.upsert(result.envelope.toEntity())
            recordingDao.setDurationAndState(
                id = result.recordingId,
                durationMs = result.durationMs,
                state = AnalysisState.ANALYZED.name,
            )
        }
    }

    override fun observeEnvelope(recordingId: Long): Flow<Envelope?> =
        envelopeDao.observeForRecording(recordingId).map { it?.toDomain() }
}
