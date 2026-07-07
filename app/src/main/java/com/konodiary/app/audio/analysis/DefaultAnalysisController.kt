package com.konodiary.app.audio.analysis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.AudioAnalyzer
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultAnalysisController(
    context: Context,
    private val scope: CoroutineScope,
    private val recordingRepository: RecordingRepository,
    private val audioAnalyzer: AudioAnalyzer,
) : AnalysisController {

    private val appContext = context.applicationContext

    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    override val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    // MediaCodec instances are scarce and decodes are CPU-heavy, so analyses run
    // ONE AT A TIME: startAnalysis enqueues, a single worker coroutine drains.
    private val queue = Channel<Long>(Channel.UNLIMITED)

    init {
        // 워커 풀: 기본 1개, 전원이 연결되어 있으면 최대 MAX_WORKERS개 병렬.
        // 첫 가져오기 백로그(수백 개)를 현실적인 시간 안에 소화하기 위한 것으로,
        // 보조 워커는 충전 중에만 큐에서 꺼낸다(배터리 소모·발열 보호).
        repeat(MAX_WORKERS) { index ->
            scope.launch {
                while (true) {
                    if (index > 0 && !isPlugged()) {
                        delay(PLUG_POLL_MS)
                        continue
                    }
                    val recordingId = queue.receiveCatching().getOrNull() ?: break
                    // Isolate per-run failures so the worker keeps draining the queue.
                    try {
                        analyze(recordingId)
                    } catch (t: Throwable) {
                        runCatching { recordingRepository.setAnalysisState(recordingId, AnalysisState.FAILED) }
                    } finally {
                        removeProgress(recordingId)
                    }
                }
            }
        }
    }

    private fun isPlugged(): Boolean {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    override fun startAnalysis(recordingId: Long) {
        // No-op if already running or waiting in the queue (progress map is the
        // single source of truth for "in flight"). Register at 0f first so a
        // waiting recording shows as "analyzing 0%", then enqueue.
        var wasEmpty = false
        synchronized(_progress) {
            if (_progress.value.containsKey(recordingId)) return
            wasEmpty = _progress.value.isEmpty()
            _progress.value = _progress.value.toMutableMap().apply { put(recordingId, 0f) }
        }
        queue.trySend(recordingId)
        // On the empty -> non-empty transition, protect the queue with the
        // foreground service. Redundant calls while it is already running are
        // harmless, so we only need to fire on the transition.
        if (wasEmpty) AnalysisService.start(appContext)
    }

    override fun enqueueAllPending() {
        scope.launch {
            recordingRepository.observeRecordings().first()
                .filter { it.analysisState == AnalysisState.NOT_ANALYZED }
                .forEach { startAnalysis(it.id) }
        }
    }

    /** True while [recordingId] is running or waiting in the queue. */
    fun isRunning(recordingId: Long): Boolean =
        _progress.value.containsKey(recordingId)

    private suspend fun analyze(recordingId: Long) {
        val recording = recordingRepository.getRecording(recordingId) ?: return
        recordingRepository.setAnalysisState(recordingId, AnalysisState.ANALYZING)

        val raw = audioAnalyzer.analyze(recording.fileUri) { p ->
            updateProgress(recordingId, p)
        }

        val result = AnalysisResult(
            recordingId = recordingId,
            durationMs = raw.durationMs,
            segments = raw.segments,
            envelope = Envelope(recordingId, raw.envelope.frameMs, raw.envelope.data),
        )
        recordingRepository.saveAnalysisResult(result)
    }

    private fun updateProgress(id: Long, value: Float) {
        synchronized(_progress) {
            _progress.value = _progress.value.toMutableMap().apply { put(id, value) }
        }
    }

    private fun removeProgress(id: Long) {
        synchronized(_progress) {
            _progress.value = _progress.value.toMutableMap().apply { remove(id) }
        }
    }

    companion object {
        /** 전원 연결 시 동시 분석 개수 (배터리에서는 1개만). */
        private const val MAX_WORKERS = 3
        private const val PLUG_POLL_MS = 5_000L
    }
}
