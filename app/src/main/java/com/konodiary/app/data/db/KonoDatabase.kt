package com.konodiary.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecordingEntity::class,
        SegmentEntity::class,
        SongEntity::class,
        EnvelopeEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KonoDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun segmentDao(): SegmentDao
    abstract fun songDao(): SongDao
    abstract fun envelopeDao(): EnvelopeDao

    companion object {
        @Volatile
        private var instance: KonoDatabase? = null

        fun getInstance(context: Context): KonoDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): KonoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KonoDatabase::class.java,
                "kono.db",
            ).build()
    }
}
