package com.konodiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Clip
import com.konodiary.app.core.model.Segment
import com.konodiary.app.core.model.Song
import com.konodiary.app.ui.common.formatDuration
import com.konodiary.app.ui.common.parseDuration
import com.konodiary.app.ui.components.*
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 파형 히어로 — 카드 없이 여백 위에 바로.
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
                    AnalysisState.ANALYZING -> Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "노래 구간 분석 중…",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    AnalysisState.FAILED -> Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "분석에 실패했어요",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "파일을 다시 분석해 보세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { container.analysisController.startAnalysis(recordingId) },
                        ) { Text("다시 시도") }
                    }
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

            // 분석 전 상태에서 아직 구간이 없으면 빈 상태 안내를 보여준다 (표시 전용).
            if (segments.isEmpty() &&
                (rec == null || rec.analysisState == AnalysisState.NOT_ANALYZED)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "아직 분석 전이에요",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "노래 구간 찾기로 노래를 자동 감지해 보세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 원형 인덱스 뱃지.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${formatDuration(segment.startMs)} ~ ${formatDuration(segment.endMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "길이 ${formatDuration(segment.durationMs)} · 신뢰도 ${(segment.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                }
            }
            Spacer(Modifier.height(8.dp))
            RatingStars(rating = segment.rating, onRatingChange = onRate)
            Spacer(Modifier.height(8.dp))
            if (song != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAssign)
                        .padding(4.dp),
                ) {
                    AlbumArtThumb(artworkUrl = song.artworkUrl, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "변경",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                FilledTonalButton(onClick = onAssign) { Text("곡 등록") }
            }
            Spacer(Modifier.height(4.dp))
            // 하단 액션 행: 구간 넛지 + 삭제 (기능 동일).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val nudgeColors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { onNudgeStart(-5000) }, colors = nudgeColors) {
                        Text("시작 -5s")
                    }
                    TextButton(onClick = { onNudgeStart(5000) }, colors = nudgeColors) {
                        Text("+5s")
                    }
                    TextButton(onClick = { onNudgeEnd(-5000) }, colors = nudgeColors) {
                        Text("끝 -5s")
                    }
                    TextButton(onClick = { onNudgeEnd(5000) }, colors = nudgeColors) {
                        Text("+5s")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    Text(
                        "녹음 길이: ${formatDuration(durationMs)} 이내로 입력하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
