package com.konodiary.app.data.mapper

import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import com.konodiary.app.core.model.Recording
import com.konodiary.app.core.model.Segment
import com.konodiary.app.core.model.SegmentSource
import com.konodiary.app.core.model.Song
import com.konodiary.app.core.model.SongSummary
import com.konodiary.app.core.model.Take
import com.konodiary.app.data.db.EnvelopeEntity
import com.konodiary.app.data.db.RecordingEntity
import com.konodiary.app.data.db.SegmentEntity
import com.konodiary.app.data.db.SongEntity
import com.konodiary.app.data.db.SongSummaryRow
import com.konodiary.app.data.db.TakeRow
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ---- Recording ----

fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    fileUri = fileUri,
    displayName = displayName,
    durationMs = durationMs,
    importedAt = importedAt,
    analysisState = runCatching { AnalysisState.valueOf(analysisState) }
        .getOrDefault(AnalysisState.NOT_ANALYZED),
)

// ---- Song ----

fun SongEntity.toDomain(): Song =
    Song(id = id, title = title, artist = artist, artworkUrl = artworkUrl)

fun SongSummaryRow.toDomain(): SongSummary = SongSummary(
    song = Song(id = id, title = title, artist = artist, artworkUrl = artworkUrl),
    takeCount = takeCount,
    bestRating = bestRating,
)

// ---- Segment ----

fun SegmentEntity.toDomain(): Segment = Segment(
    id = id,
    recordingId = recordingId,
    startMs = startMs,
    endMs = endMs,
    songId = songId,
    rating = rating,
    memo = memo,
    source = runCatching { SegmentSource.valueOf(source) }
        .getOrDefault(SegmentSource.MANUAL),
    confidence = confidence,
)

fun TakeRow.toDomain(): Take = Take(
    segment = segment.toDomain(),
    recordingId = segment.recordingId,
    recordingName = recordingName,
    recordingFileUri = recordingFileUri,
    recordingImportedAt = recordingImportedAt,
)

// ---- Envelope <-> BLOB (little-endian float32) ----

fun FloatArray.toLittleEndianBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.toFloatArrayLE(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val out = FloatArray(size / 4)
    for (i in out.indices) out[i] = buffer.float
    return out
}

fun EnvelopeEntity.toDomain(): Envelope =
    Envelope(recordingId = recordingId, frameMs = frameMs, data = data.toFloatArrayLE())

fun Envelope.toEntity(): EnvelopeEntity =
    EnvelopeEntity(recordingId = recordingId, frameMs = frameMs, data = data.toLittleEndianBytes())
