package com.konodiary.app.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Recording
import com.konodiary.app.ui.common.formatDuration
import com.konodiary.app.ui.rememberContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenRecording: (Long) -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recordings by container.recordingRepository.observeRecordings()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by container.analysisController.progress
        .collectAsStateWithLifecycle()

    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        scope.launch {
            val (name, duration) = withContext(Dispatchers.IO) {
                readDisplayName(context, uri) to readDurationMs(context, uri)
            }
            container.recordingRepository.importRecording(uri.toString(), name, duration)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("코노 다이어리") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                // 일부 보이스레코더는 오디오 전용 .3gp/.3ga에 video/3gpp MIME을 붙여
                // audio/* 필터만으로는 선택이 불가능해진다.
                onClick = { importLauncher.launch(arrayOf("audio/*", "video/3gpp")) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("가져오기") },
            )
        },
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("아직 가져온 녹음이 없어요.\n오른쪽 아래 버튼으로 오디오 파일을 가져오세요.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recordings, key = { it.id }) { recording ->
                    RecordingRow(
                        recording = recording,
                        progress = progress[recording.id],
                        onClick = { onOpenRecording(recording.id) },
                        onDelete = { pendingDeleteId = recording.id },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { deleteId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.recordingRepository.deleteRecording(deleteId) }
                        pendingDeleteId = null
                    },
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("취소") }
            },
            title = { Text("녹음 삭제") },
            text = { Text("이 녹음과 등록된 구간·곡·별점·메모가 모두 삭제됩니다. 되돌릴 수 없습니다.") },
        )
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    progress: Float?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${formatDuration(recording.durationMs)} · ${stateLabel(recording.analysisState)}",
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "삭제")
            }
        }
    }
}

private fun stateLabel(state: AnalysisState): String = when (state) {
    AnalysisState.NOT_ANALYZED -> "미분석"
    AnalysisState.ANALYZING -> "분석 중"
    AnalysisState.ANALYZED -> "분석 완료"
    AnalysisState.FAILED -> "분석 실패"
}

private fun readDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "녹음"
            }
        }
    return uri.lastPathSegment ?: "녹음"
}

private fun readDurationMs(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (t: Throwable) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}
