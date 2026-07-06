package com.konodiary.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordingEntity::class,
        SegmentEntity::class,
        SongEntity::class,
        EnvelopeEntity::class,
    ],
    version = 2,
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

        /** Adds songs.artworkUrl (iTunes album art). Never destructive — real user data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN artworkUrl TEXT")
            }
        }

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
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
