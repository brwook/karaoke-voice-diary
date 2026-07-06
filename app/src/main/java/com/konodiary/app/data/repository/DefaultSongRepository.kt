package com.konodiary.app.data.repository

import com.konodiary.app.core.contracts.SongRepository
import com.konodiary.app.core.model.Song
import com.konodiary.app.core.model.SongSummary
import com.konodiary.app.core.model.Take
import com.konodiary.app.data.db.KonoDatabase
import com.konodiary.app.data.db.SongEntity
import com.konodiary.app.data.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSongRepository(private val db: KonoDatabase) : SongRepository {

    private val songDao = db.songDao()

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSongsWithTakes(): Flow<List<SongSummary>> =
        songDao.observeSongsWithTakes().map { list -> list.map { it.toDomain() } }

    override fun observeTakesForSong(songId: Long): Flow<List<Take>> =
        songDao.observeTakesForSong(songId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSong(id: Long): Song? =
        songDao.getById(id)?.toDomain()

    override suspend fun createSong(title: String, artist: String): Long =
        songDao.insert(SongEntity(title = title.trim(), artist = artist.trim()))

    override suspend fun deleteSong(id: Long) =
        songDao.deleteById(id)
}
