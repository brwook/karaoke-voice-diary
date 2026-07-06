package com.konodiary.app.ui.components

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konodiary.app.ui.rememberContainer

/**
 * 연결된 보이스레코더 폴더를 관리하는 다이얼로그 (SPEC §7.6).
 *
 * - 연결된 폴더 목록: 읽기 좋은 경로 + 해제 버튼.
 * - 폴더 추가: 삼성 추천 경로 2개(각각 해당 위치에서 picker가 열리게 initial URI 전달) +
 *   "다른 폴더 직접 선택"(기본 위치).
 *
 * 새 폴더가 연결되면 [onConnected]를 호출해 HomeScreen이 즉시 스캔하도록 알린다.
 * takePersistableUriPermission은 manager가 처리하므로 여기서 호출하지 않는다.
 *
 * @param connectedFolders 현재 연결된 tree URI 목록(문자열).
 * @param onConnected 새 폴더가 연결된 직후 호출 — 즉시 스캔 트리거.
 */
@Composable
fun FolderManageDialog(
    connectedFolders: List<String>,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    val container = rememberContainer()
    val folderSyncManager = container.folderSyncManager

    // 런처는 하나만 두고, 어떤 추천 경로를 탭했는지에 따라 initial URI(input)만 바꿔 호출한다.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        folderSyncManager.connectFolder(uri.toString())
        onConnected()
    }

    // 이미 연결된 폴더를 표시 경로 기준으로 판정하기 위한 집합.
    val connectedDisplayPaths = connectedFolders.map { treeUriToDisplayPath(it) }.toSet()

    val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text("폴더 관리") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 연결된 폴더 목록
                if (connectedFolders.isEmpty()) {
                    Text(
                        "연결된 폴더가 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                } else {
                    SectionHeaderText("연결된 폴더")
                    connectedFolders.forEach { treeUri ->
                        ConnectedFolderRow(
                            displayPath = treeUriToDisplayPath(treeUri),
                            onDisconnect = { folderSyncManager.disconnectFolder(treeUri) },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 폴더 추가 섹션
                SectionHeaderText("폴더 추가")
                if (isSamsung) {
                    Text(
                        "삼성 보이스레코더 기본 경로",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }

                RECOMMENDED_PATHS.forEach { rec ->
                    val already = rec.displayPath in connectedDisplayPaths
                    RecommendedPathRow(
                        label = rec.label,
                        subtitle = rec.subtitle,
                        connected = already,
                        onClick = { folderPickerLauncher.launch(buildInitialUri(rec.path)) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPickerLauncher.launch(null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("다른 폴더 직접 선택", fontWeight = FontWeight.Medium)
                }
            }
        },
    )
}

/** 삼성 보이스레코더 추천 경로 (SPEC §7.6). */
private data class RecommendedPath(
    val label: String,
    val subtitle: String,
    /** "primary:" 이후의 상대 경로. initial URI / 표시 경로 판정에 쓰인다. */
    val path: String,
) {
    val displayPath: String get() = path
}

private val RECOMMENDED_PATHS = listOf(
    RecommendedPath(
        label = "최근 녹음 폴더",
        subtitle = "Recordings/Voice Recorder (2023년 10월 이후)",
        path = "Recordings/Voice Recorder",
    ),
    RecommendedPath(
        label = "옛 녹음 폴더",
        subtitle = "Voice Recorder (그 이전)",
        path = "Voice Recorder",
    ),
)

/** OpenDocumentTree picker가 [relativePath] 위치에서 열리도록 initial document URI를 만든다. */
private fun buildInitialUri(relativePath: String): Uri =
    DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:$relativePath",
    )

/**
 * tree URI를 사람이 읽기 좋은 경로로 변환하는 순수 헬퍼.
 *
 * tree URI의 docId(마지막 "tree/" 세그먼트를 URL 디코딩)에서 "primary:" 접두를 제거해
 * "Recordings/Voice Recorder" 형태로 만든다. 파싱에 실패하면 원문 뒷부분을 그대로 돌려준다.
 */
internal fun treeUriToDisplayPath(treeUri: String): String {
    val marker = "tree/"
    val idx = treeUri.lastIndexOf(marker)
    val docId = if (idx >= 0) {
        treeUri.substring(idx + marker.length)
    } else {
        return treeUri.substringAfterLast('/')
    }
    val decoded = runCatching { Uri.decode(docId) }.getOrNull() ?: return docId
    return decoded.removePrefix("primary:").ifBlank { decoded }
}

@Composable
private fun SectionHeaderText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConnectedFolderRow(displayPath: String, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(
            displayPath,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDisconnect) {
            Icon(Icons.Filled.Close, contentDescription = "연결 해제")
        }
    }
}

@Composable
private fun RecommendedPathRow(
    label: String,
    subtitle: String,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (connected) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = if (connected) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.Medium,
                color = if (connected) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connected) {
            Spacer(Modifier.width(8.dp))
            Text(
                "연결됨",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
