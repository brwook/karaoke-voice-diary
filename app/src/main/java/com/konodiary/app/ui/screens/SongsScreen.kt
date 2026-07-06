package com.konodiary.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.SongSummary
import com.konodiary.app.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(onOpenSong: (Long) -> Unit) {
    val container = rememberContainer()
    val songs by container.songRepository.observeSongsWithTakes()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("노래") }) },
    ) { padding ->
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("등록된 곡이 없어요.\n녹음 상세에서 구간에 곡을 등록해 보세요.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(songs, key = { it.song.id }) { summary ->
                    SongRow(summary = summary, onClick = { onOpenSong(summary.song.id) })
                }
            }
        }
    }
}

@Composable
private fun SongRow(summary: SongSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(summary.song.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${summary.song.artist} · 테이크 ${summary.takeCount}개")
            }
            if (summary.bestRating > 0) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Text(summary.bestRating.toString())
            }
        }
    }
}
