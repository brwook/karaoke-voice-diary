package com.konodiary.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    val displayName: String,
    val durationMs: Long,
    val importedAt: Long,
    val analysisState: String,
)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("recordingId"), Index("songId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val songId: Long?,
    val rating: Int,
    val memo: String,
    val source: String,
    val confidence: Float,
)

@Entity(
    tableName = "envelopes",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EnvelopeEntity(
    @PrimaryKey val recordingId: Long,
    val frameMs: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnvelopeEntity) return false
        return recordingId == other.recordingId &&
            frameMs == other.frameMs &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = recordingId.hashCode()
        result = 31 * result + frameMs
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/** Row for the songs list: song + aggregate stats. */
data class SongSummaryRow(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val takeCount: Int,
    val bestRating: Int,
)

/** Row for song detail: a segment plus its source recording's name/date. */
data class TakeRow(
    @androidx.room.Embedded val segment: SegmentEntity,
    val recordingName: String,
    val recordingFileUri: String,
    val recordingImportedAt: Long,
)

/** Row for per-recording segment counts: total segments + those with a song assigned. */
data class SegmentCountsRow(
    val recordingId: Long,
    val total: Int,
    val registered: Int,
)
