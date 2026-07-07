package com.konodiary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.Clip
import com.konodiary.app.core.model.Song
import com.konodiary.app.core.model.Take
import com.konodiary.app.ui.common.formatDuration
import com.konodiary.app.ui.components.AlbumArtThumb
import com.konodiary.app.ui.components.ChipTone
import com.konodiary.app.ui.components.RatingStars
import com.konodiary.app.ui.components.StatusChip
import com.konodiary.app.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(songId: Long, onBack: () -> Unit) {
    val container = rememberContainer()

    var song by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(songId) { song = container.songRepository.getSong(songId) }

    val takes by container.songRepository.observeTakesForSong(songId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val bestRating = takes.maxOfOrNull { it.segment.rating } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song?.let { "${it.title} · ${it.artist}" } ?: "곡", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        if (takes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "이 곡의 테이크가 없어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                song?.let { s ->
                    item(key = "header") {
                        SongHeader(song = s)
                    }
                }
                itemsIndexed(takes, key = { _, t -> t.segment.id }) { _, take ->
                    TakeRow(
                        take = take,
                        isBest = bestRating > 0 && take.segment.rating == bestRating,
                        onPlay = {
                            container.segmentPlayer.play(
                                Clip(
                                    uri = take.recordingFileUri,
                                    title = song?.title ?: take.recordingName,
                                    startMs = take.segment.startMs,
                                    endMs = take.segment.endMs,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SongHeader(song: Song) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtThumb(song.artworkUrl, size = 96.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TakeRow(take: Take, isBest: Boolean, onPlay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(take.segment.durationMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isBest) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(text = "BEST", tone = ChipTone.GOLD)
                    }
                }
                Text(
                    take.recordingName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (take.segment.rating > 0) {
                    Spacer(Modifier.padding(top = 4.dp))
                    RatingStars(rating = take.segment.rating, starSize = 18.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
            }
        }
    }
}
