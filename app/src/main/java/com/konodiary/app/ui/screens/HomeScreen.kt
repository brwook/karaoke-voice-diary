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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.contracts.FolderSyncManager
import com.konodiary.app.core.contracts.ScanResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Recording
import com.konodiary.app.ui.common.formatDuration
import com.konodiary.app.ui.components.*
import com.konodiary.app.ui.rememberContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
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
    val snackbarHostState = remember { SnackbarHostState() }
    val folderSyncManager = container.folderSyncManager

    val recordings by container.recordingRepository.observeRecordings()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by container.analysisController.progress
        .collectAsStateWithLifecycle()
    val folders by folderSyncManager.folders
        .collectAsStateWithLifecycle()

    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showFolderManage by remember { mutableStateOf(false) }
    // scanAndImport 중복 실행 방지 플래그.
    val scanning = remember { mutableStateOf(false) }

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
            val newId = container.recordingRepository.importRecording(uri.toString(), name, duration)
            container.analysisController.startAnalysis(newId)
        }
    }

    // 홈 진입 시 폴더가 연결되어 있으면 조용히 자동 스캔 (imported > 0일 때만 스낵바).
    LaunchedEffect(Unit) {
        if (folderSyncManager.folders.value.isNotEmpty()) {
            runScan(
                scope = scope,
                manager = folderSyncManager,
                scanning = scanning,
                snackbarHostState = snackbarHostState,
                manual = false,
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("코노 다이어리") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menuExpanded = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("가져오기") },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("파일 직접 선택") },
                        onClick = {
                            menuExpanded = false
                            // 일부 보이스레코더는 오디오 전용 .3gp/.3ga에 video/3gpp MIME을
                            // 붙여 audio/* 필터만으로는 선택이 불가능해진다.
                            importLauncher.launch(arrayOf("audio/*", "video/3gpp"))
                        },
                    )
                    if (folders.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("폴더 스캔") },
                            onClick = {
                                menuExpanded = false
                                runScan(
                                    scope = scope,
                                    manager = folderSyncManager,
                                    scanning = scanning,
                                    snackbarHostState = snackbarHostState,
                                    manual = true,
                                )
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (folders.isEmpty()) "보이스레코더 폴더 연결" else "폴더 관리",
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showFolderManage = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "아직 녹음이 없어요",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "가져오기로 코노 녹음을 불러오세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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

    if (showFolderManage) {
        FolderManageDialog(
            connectedFolders = folders,
            onDismiss = { showFolderManage = false },
            onConnected = {
                // 연결 직후 다이얼로그는 유지한 채 즉시 스캔.
                runScan(
                    scope = scope,
                    manager = folderSyncManager,
                    scanning = scanning,
                    snackbarHostState = snackbarHostState,
                    manual = true,
                )
            },
        )
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

/**
 * 스캔 실행 공통 함수. [scanning] 플래그로 중복 실행을 막고, 결과에 따라 스낵바를 띄운다.
 * [manual]이 false(자동 스캔)이면 imported > 0일 때만 스낵바를 표시한다.
 */
private fun runScan(
    scope: CoroutineScope,
    manager: FolderSyncManager,
    scanning: androidx.compose.runtime.MutableState<Boolean>,
    snackbarHostState: SnackbarHostState,
    manual: Boolean,
) {
    if (scanning.value) return
    scanning.value = true
    scope.launch {
        try {
            val result: ScanResult = manager.scanAndImport()
            val message = when {
                result.imported > 0 -> buildString {
                    append("새 녹음 ${result.imported}개 가져옴 · 분석 시작")
                    if (result.failed > 0) append(" · 실패 ${result.failed}개")
                }
                manual -> "새 파일 없음"
                else -> null
            }
            if (message != null) snackbarHostState.showSnackbar(message)
        } catch (t: Throwable) {
            if (manual) {
                snackbarHostState.showSnackbar("폴더 스캔 실패 — 연결을 다시 확인하세요")
            }
        } finally {
            scanning.value = false
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    progress: Float?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recording.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatDate(recording.importedAt)} · ${formatDuration(recording.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = stateChipText(recording.analysisState, progress),
                    tone = stateChipTone(recording.analysisState),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

private fun stateChipText(state: AnalysisState, progress: Float?): String = when (state) {
    AnalysisState.NOT_ANALYZED -> "미분석"
    AnalysisState.ANALYZING ->
        if (progress != null) "분석 중 ${(progress * 100).toInt()}%" else "분석 중"
    AnalysisState.ANALYZED -> "분석 완료"
    AnalysisState.FAILED -> "분석 실패"
}

private fun stateChipTone(state: AnalysisState): ChipTone = when (state) {
    AnalysisState.NOT_ANALYZED -> ChipTone.NEUTRAL
    AnalysisState.ANALYZING -> ChipTone.ACTIVE
    AnalysisState.ANALYZED -> ChipTone.SUCCESS
    AnalysisState.FAILED -> ChipTone.ERROR
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(epochMs))

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
