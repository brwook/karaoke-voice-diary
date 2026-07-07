package com.konodiary.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konodiary.app.core.model.SongSearchResult
import com.konodiary.app.core.model.SongSummary
import com.konodiary.app.ui.rememberContainer
import com.konodiary.app.ui.theme.StarGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 검색 우선 곡 등록 다이얼로그 (SPEC §7.5).
 *
 * 검색어를 입력하면 500ms 디바운스 후 iTunes 검색이 돌고, "내 곡"(이미 등록된 곡) 섹션과
 * "온라인 검색" 섹션을 함께 보여준다. 곡을 탭하면 [onAssign]에 songId를 넘겨 즉시 배정한다.
 * 별점 UI는 여기 없다(등록과 평가 분리). 검색 실패/오프라인 대비 "직접 입력" 폴백을 항상 제공한다.
 *
 * @param onAssign 선택된(또는 새로 만든) 곡의 songId로 구간을 배정하고 다이얼로그를 닫는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerDialog(
    onDismiss: () -> Unit,
    onAssign: (Long) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    val mySongs by container.songRepository.observeSongsWithTakes()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    // 검색이 최소 한 번 시도되었는지 (0개 결과 vs. 아직 안 함 구분용).
    var searched by remember { mutableStateOf(false) }

    var showManualEntry by remember { mutableStateOf(false) }

    val trimmed = query.trim()

    // 검색어 2자 이상 + 500ms 디바운스로 자동 검색. query가 바뀌면 이전 LaunchedEffect가 취소되어
    // delay가 끊기므로 자연스러운 디바운스가 된다.
    LaunchedEffect(trimmed) {
        if (trimmed.length < 2) {
            results = emptyList()
            searching = false
            searchFailed = false
            searched = false
            return@LaunchedEffect
        }
        delay(500)
        searching = true
        searchFailed = false
        try {
            results = container.songSearchService.search(trimmed)
        } catch (e: Exception) {
            searchFailed = true
            results = emptyList()
        } finally {
            searching = false
            searched = true
        }
    }

    // 내 곡 필터: 검색어가 제목/가수에 포함(대소문자 무시). 검색어가 비면 전체 표시.
    val filteredMySongs = remember(mySongs, trimmed) {
        if (trimmed.isEmpty()) {
            mySongs
        } else {
            val q = trimmed.lowercase()
            mySongs.filter {
                it.song.title.lowercase().contains(q) || it.song.artist.lowercase().contains(q)
            }
        }
    }

    // 온라인 결과와 기존 곡을 제목+가수(트림+대소문자 무시)로 매칭하기 위한 인덱스.
    val existingByKey = remember(mySongs) {
        mySongs.associateBy { songKey(it.song.title, it.song.artist) }
    }

    fun assignSearchResult(r: SongSearchResult) {
        val existing = existingByKey[songKey(r.title, r.artist)]
        if (existing != null) {
            onAssign(existing.song.id)
        } else {
            scope.launch {
                val id = container.songRepository.findOrCreateSong(r.title, r.artist, r.artworkUrl)
                onAssign(id)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("곡 등록") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("제목 · 가수 검색") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    // 다이얼로그가 길어지지 않게 화면의 ~70% 이내로 제한하고 스크롤.
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 섹션 1: 내 곡
                    if (filteredMySongs.isNotEmpty()) {
                        item(key = "header-my") { SectionHeader("내 곡") }
                        items(filteredMySongs, key = { "my-${it.song.id}" }) { summary ->
                            MySongRow(summary = summary, onClick = { onAssign(summary.song.id) })
                        }
                    }

                    // 섹션 2: 온라인 검색
                    item(key = "header-online") { SectionHeader("온라인 검색") }

                    when {
                        trimmed.length < 2 -> item(key = "hint") {
                            HintText("2자 이상 입력하면 검색해요")
                        }
                        searching && results.isEmpty() -> item(key = "loading") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        searchFailed -> item(key = "failed") {
                            HintText(
                                "검색에 실패했어요. 네트워크를 확인하거나 아래에서 직접 입력하세요.",
                                isError = true,
                            )
                        }
                        searched && results.isEmpty() -> item(key = "empty") {
                            HintText("검색 결과 없음")
                        }
                        else -> items(results, key = { "online-${it.title}-${it.artist}-${it.album}" }) { r ->
                            val existing = existingByKey[songKey(r.title, r.artist)]
                            OnlineResultRow(
                                result = r,
                                existingTakeCount = existing?.takeCount,
                                onClick = { assignSearchResult(r) },
                            )
                        }
                    }

                    // 하단: 직접 입력 (오프라인 폴백, 항상 접근 가능)
                    item(key = "manual-toggle") {
                        TextButton(
                            onClick = { showManualEntry = !showManualEntry },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(if (showManualEntry) "직접 입력 닫기" else "직접 입력")
                        }
                    }
                    if (showManualEntry) {
                        item(key = "manual-form") {
                            ManualEntryForm(
                                initialTitle = trimmed,
                                onRegister = { title, artist ->
                                    scope.launch {
                                        val id = container.songRepository.findOrCreateSong(title, artist)
                                        onAssign(id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** "title|artist" 정규화 키 (트림 + 소문자). 기존 곡 ↔ 온라인 결과 매칭용. */
private fun songKey(title: String, artist: String): String =
    "${title.trim().lowercase()}|${artist.trim().lowercase()}"

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun MySongRow(summary: SongSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtThumb(summary.song.artworkUrl, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                summary.song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary.song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "테이크 ${summary.takeCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.bestRating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = StarGold,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(summary.bestRating.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OnlineResultRow(
    result: SongSearchResult,
    existingTakeCount: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtThumb(result.artworkUrl, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (result.album.isNullOrBlank()) {
                result.artist
            } else {
                "${result.artist} · ${result.album}"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (existingTakeCount != null) {
                StatusChip(
                    text = "등록됨 · 테이크 ${existingTakeCount}개",
                    tone = ChipTone.SUCCESS,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ManualEntryForm(
    initialTitle: String,
    onRegister: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("제목") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it },
            label = { Text("가수") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { if (title.isNotBlank()) onRegister(title.trim(), artist.trim()) },
            enabled = title.isNotBlank(),
        ) { Text("등록") }
    }
}
