package com.konodiary.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Recording
import com.konodiary.app.ui.common.EtaEstimator
import kotlinx.coroutines.delay

/**
 * 홈 상단 분석 현황 대시보드. DESIGN.md 토큰만 사용.
 *
 * @param recordings 전체 녹음 목록(파생 카운트/duration 산출용).
 * @param progress AnalysisController.progress 스냅샷 — 실행+대기 항목(대기=0f).
 * @param onRetryFailed FAILED 항목 일괄 재시도.
 */
@Composable
fun AnalysisDashboard(
    recordings: List<Recording>,
    progress: Map<Long, Float>,
    onRetryFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    val total = recordings.size
    val analyzed = recordings.count { it.analysisState == AnalysisState.ANALYZED }
    val failed = recordings.count { it.analysisState == AnalysisState.FAILED }
    val remaining = recordings.count {
        it.analysisState == AnalysisState.NOT_ANALYZED ||
            it.analysisState == AnalysisState.ANALYZING
    }

    // id -> durationMs, 그리고 0이 아닌 duration들의 평균(0 대체용).
    val durationById = remember(recordings) { recordings.associate { it.id to it.durationMs } }
    val avgKnownDuration = remember(recordings) {
        val known = recordings.map { it.durationMs }.filter { it > 0L }
        if (known.isEmpty()) 0L else known.sum() / known.size
    }
    fun durationOf(id: Long): Long {
        val d = durationById[id] ?: 0L
        return if (d > 0L) d else avgKnownDuration
    }

    val estimator = remember { EtaEstimator() }
    // 직전 progress 스냅샷 + 그 관측 시각.
    var prevSnapshot by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var prevWallMs by remember { mutableStateOf(0L) }
    // speedFactor 변화를 컴포지션에 반영하기 위한 트리거.
    var etaTick by remember { mutableStateOf(0) }

    val active = progress.isNotEmpty()

    // LaunchedEffect는 실행 시점의 파라미터를 클로저로 캡처하므로, 루프 안에서
    // 최신 값을 보려면 rememberUpdatedState를 거쳐야 한다 (안 그러면 델타가
    // 항상 0으로 관측되어 ETA가 영원히 "계산 중"에 머문다).
    val currentProgress by rememberUpdatedState(progress)
    val currentRecordings by rememberUpdatedState(recordings)

    // 분석이 진행되는 동안 1초 간격으로 처리량을 샘플링.
    LaunchedEffect(active) {
        if (!active) {
            // 진행이 멈추면 다음 시작 때 델타가 튀지 않도록 스냅샷을 비운다.
            prevSnapshot = emptyMap()
            prevWallMs = 0L
            return@LaunchedEffect
        }
        // 첫 진입 시 기준선 세팅(델타 계산 없이 스냅샷만 저장).
        prevSnapshot = currentProgress
        prevWallMs = System.currentTimeMillis()
        while (true) {
            delay(1_000L)
            val now = System.currentTimeMillis()
            val current = currentProgress
            val prev = prevSnapshot
            val wallDelta = now - prevWallMs

            val recs = currentRecordings
            val avg = recs.map { it.durationMs }.filter { it > 0L }
                .let { if (it.isEmpty()) 0L else it.sum() / it.size }
            fun durOf(id: Long): Long {
                val d = recs.firstOrNull { it.id == id }?.durationMs ?: 0L
                return if (d > 0L) d else avg
            }

            var audioDelta = 0.0
            // 직전에 있었던 id들: 진행분 또는 완료분을 오디오량으로 환산.
            for ((id, oldP) in prev) {
                val dur = durOf(id)
                if (dur <= 0L) continue
                val newP = current[id]
                audioDelta += if (newP != null) {
                    ((newP - oldP).coerceAtLeast(0f)).toDouble() * dur
                } else {
                    // 사라진 id = 완료: 남아있던 분량만큼 처리됨.
                    ((1f - oldP).coerceAtLeast(0f)).toDouble() * dur
                }
            }

            estimator.addSample(audioDelta.toLong(), wallDelta)
            prevSnapshot = current
            prevWallMs = now
            etaTick++
        }
    }

    // remainingAudioMs = Σ(NOT_ANALYZED durations) + Σ(progress에 있는 id의 (1-p)*duration).
    val remainingAudioMs: Long = run {
        var sum = 0L
        for (r in recordings) {
            if (r.analysisState == AnalysisState.NOT_ANALYZED && !progress.containsKey(r.id)) {
                sum += durationOf(r.id)
            }
        }
        for ((id, p) in progress) {
            val dur = durationOf(id)
            sum += (((1f - p).coerceAtLeast(0f)).toDouble() * dur).toLong()
        }
        sum
    }
    // etaTick을 읽어, estimator 내부 상태(비관측)가 갱신될 때마다 etaMs/speedFactor가
    // 재계산되도록 스냅샷 읽기 의존성을 건다.
    @Suppress("UNUSED_VARIABLE")
    val tickDep = etaTick
    val etaMs = estimator.etaMs(remainingAudioMs)
    val speedFactor = estimator.speedFactor

    // 현재 처리 중(진행률 최대) 항목.
    val currentEntry = progress.maxByOrNull { it.value }
    val currentName = currentEntry?.let { entry ->
        recordings.firstOrNull { it.id == entry.key }?.displayName
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "분석 현황",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )

            if (remaining == 0 && failed == 0) {
                // 축약 모드: 한 줄 요약 + 꽉 찬 진행바.
                Spacer(Modifier.height(8.dp))
                Text(
                    "녹음 ${total}개 · 모두 분석 완료",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 1f },
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            // 상태 숫자 3열.
            Row(modifier = Modifier.fillMaxWidth()) {
                StatColumn(
                    value = analyzed,
                    label = "완료",
                    valueColor = scheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = remaining,
                    label = "대기",
                    valueColor = scheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = failed,
                    label = "실패",
                    valueColor = if (failed > 0) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            // 전체 진행바 + 우측 비율 텍스트.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (total > 0) analyzed.toFloat() / total else 0f },
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$analyzed/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (remaining > 0) {
                Spacer(Modifier.height(12.dp))
                if (currentName != null) {
                    Text(
                        currentName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    etaLine(etaMs, speedFactor),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.tertiary,
                )
            }

            if (failed > 0) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRetryFailed) {
                    Text(
                        "실패 ${failed}개 모두 재시도",
                        color = scheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    value: Int,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * ETA 표시 한 줄.
 * - speedFactor 미상: "예상 시간 계산 중…"
 * - 그 외: "예상 남은 시간 " + 포맷된 시간(<60초 "1분 미만" / <60분 "약 N분" / "약 N시간 M분").
 */
private fun etaLine(etaMs: Long?, speedFactor: Double?): String {
    if (speedFactor == null || etaMs == null) return "예상 시간 계산 중…"
    val totalSeconds = etaMs / 1000
    val body = when {
        totalSeconds < 60 -> "1분 미만"
        totalSeconds < 3600 -> "약 ${totalSeconds / 60}분"
        else -> {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            "약 ${hours}시간 ${minutes}분"
        }
    }
    return "예상 남은 시간 $body"
}
