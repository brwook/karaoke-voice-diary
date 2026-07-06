package com.konodiary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Clip
import com.konodiary.app.core.model.Segment
import com.konodiary.app.core.model.Song
import com.konodiary.app.ui.common.formatDuration
import com.konodiary.app.ui.common.parseDuration
import com.konodiary.app.ui.components.SongPickerDialog
import com.konodiary.app.ui.components.Waveform
import com.konodiary.app.ui.rememberContainer
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(recordingId: Long, onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    val recording by container.recordingRepository.observeRecording(recordingId)
        .collectAsStateWithLifecycle(initialValue = null)
    val segments by container.segmentRepository.observeSegments(recordingId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.songRepository.observeSongs()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val envelope by container.recordingRepository.observeEnvelope(recordingId)
        .collectAsStateWithLifecycle(initialValue = null)
    val progressMap by container.analysisController.progress.collectAsStateWithLifecycle()

    val rec = recording
    val progress = progressMap[recordingId]
    val songsById = remember(songs) { songs.associateBy { it.id } }

    var assignFor by remember { mutableStateOf<Segment?>(null) }
    var showManualAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rec?.displayName ?: "녹음", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Waveform(
                    envelope = envelope,
                    color = MaterialTheme.colorScheme.primary,
                    segments = segments,
                    totalDurationMs = rec?.durationMs ?: 0L,
                    segmentColor = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                when (rec?.analysisState) {
                    AnalysisState.ANALYZING -> {
                        Column {
                            Text("노래 구간 분석 중…")
                            LinearProgressIndicator(
                                progress = { progress ?: 0f },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                    AnalysisState.FAILED -> Button(
                        onClick = { container.analysisController.startAnalysis(recordingId) },
                    ) { Text("분석 실패 — 재시도") }
                    AnalysisState.ANALYZED, AnalysisState.NOT_ANALYZED, null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { container.analysisController.startAnalysis(recordingId) }) {
                            Text(if (rec?.analysisState == AnalysisState.ANALYZED) "다시 분석" else "노래 구간 찾기")
                        }
                        OutlinedButton(onClick = { showManualAdd = true }) { Text("구간 수동 추가") }
                    }
                }
            }

            items(segments, key = { it.id }) { segment ->
                SegmentCard(
                    index = segments.indexOf(segment) + 1,
                    segment = segment,
                    song = segment.songId?.let { songsById[it] },
                    onPlay = {
                        val title = segment.songId?.let { songsById[it]?.title } ?: "구간"
                        rec?.let {
                            container.segmentPlayer.play(
                                Clip(it.fileUri, title, segment.startMs, segment.endMs)
                            )
                        }
                    },
                    onRate = { r -> scope.launch { container.segmentRepository.setRating(segment.id, r) } },
                    onAssign = { assignFor = segment },
                    onNudgeStart = { delta ->
                        scope.launch {
                            container.segmentRepository.updateBounds(
                                segment.id,
                                (segment.startMs + delta).coerceIn(0, segment.endMs - 1000),
                                segment.endMs,
                            )
                        }
                    },
                    onNudgeEnd = { delta ->
                        scope.launch {
                            val minEnd = segment.startMs + 1000
                            val maxEnd = (rec?.durationMs ?: Long.MAX_VALUE).coerceAtLeast(minEnd)
                            container.segmentRepository.updateBounds(
                                segment.id,
                                segment.startMs,
                                (segment.endMs + delta).coerceIn(minEnd, maxEnd),
                            )
                        }
                    },
                    onDelete = { scope.launch { container.segmentRepository.deleteSegment(segment.id) } },
                )
            }
        }
    }

    assignFor?.let { seg ->
        SongPickerDialog(
            onDismiss = { assignFor = null },
            onAssign = { songId ->
                scope.launch { container.segmentRepository.assignSong(seg.id, songId) }
                assignFor = null
            },
        )
    }

    if (showManualAdd) {
        ManualSegmentDialog(
            durationMs = rec?.durationMs ?: 0L,
            onDismiss = { showManualAdd = false },
            onAdd = { startMs, endMs ->
                scope.launch { container.segmentRepository.addManualSegment(recordingId, startMs, endMs) }
                showManualAdd = false
            },
        )
    }
}

@Composable
private fun SegmentCard(
    index: Int,
    segment: Segment,
    song: Song?,
    onPlay: () -> Unit,
    onRate: (Int) -> Unit,
    onAssign: () -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#$index  ${formatDuration(segment.startMs)} ~ ${formatDuration(segment.endMs)} (${formatDuration(segment.durationMs)})",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(song?.let { "${it.title} · ${it.artist}" } ?: "곡 미지정")
                }
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }
            StarRating(rating = segment.rating, onRate = onRate)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onAssign) { Text(if (song == null) "곡 등록" else "곡 변경") }
                TextButton(onClick = { onNudgeStart(-5000) }) { Text("시작 -5s") }
                TextButton(onClick = { onNudgeStart(5000) }) { Text("+5s") }
                TextButton(onClick = { onNudgeEnd(-5000) }) { Text("끝 -5s") }
                TextButton(onClick = { onNudgeEnd(5000) }) { Text("+5s") }
            }
        }
    }
}

@Composable
private fun StarRating(rating: Int, onRate: (Int) -> Unit) {
    Row {
        for (i in 1..5) {
            IconButton(onClick = { onRate(if (rating == i) 0 else i) }) {
                Icon(
                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$i 점",
                )
            }
        }
    }
}

@Composable
private fun ManualSegmentDialog(
    durationMs: Long,
    onDismiss: () -> Unit,
    onAdd: (Long, Long) -> Unit,
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val startMs = parseDuration(start)
    val endMs = parseDuration(end)
    // When the recording length is known, keep the range inside the file so the
    // clip does not exceed the media and play back empty / unpredictably.
    val withinDuration = durationMs <= 0L ||
        (startMs != null && endMs != null && startMs < durationMs && endMs <= durationMs)
    val valid = startMs != null && endMs != null && endMs > startMs && withinDuration
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (valid) onAdd(startMs!!, endMs!!) },
                enabled = valid,
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("구간 수동 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = start, onValueChange = { start = it },
                    label = { Text("시작 (mm:ss)") }, singleLine = true,
                )
                OutlinedTextField(
                    value = end, onValueChange = { end = it },
                    label = { Text("끝 (mm:ss)") }, singleLine = true,
                )
                if (durationMs > 0L) {
                    Text("녹음 길이: ${formatDuration(durationMs)} 이내로 입력하세요")
                }
            }
        },
    )
}
