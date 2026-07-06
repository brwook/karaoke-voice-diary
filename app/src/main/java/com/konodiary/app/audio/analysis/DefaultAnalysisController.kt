package com.konodiary.app.audio.analysis

import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.AudioAnalyzer
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultAnalysisController(
    private val scope: CoroutineScope,
    private val recordingRepository: RecordingRepository,
    private val audioAnalyzer: AudioAnalyzer,
) : AnalysisController {

    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    override val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    // MediaCodec instances are scarce and decodes are CPU-heavy, so analyses run
    // ONE AT A TIME: startAnalysis enqueues, a single worker coroutine drains.
    private val queue = Channel<Long>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (recordingId in queue) {
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

    override fun startAnalysis(recordingId: Long) {
        // No-op if already running or waiting in the queue (progress map is the
        // single source of truth for "in flight"). Register at 0f first so a
        // waiting recording shows as "analyzing 0%", then enqueue.
        synchronized(_progress) {
            if (_progress.value.containsKey(recordingId)) return
            _progress.value = _progress.value.toMutableMap().apply { put(recordingId, 0f) }
        }
        queue.trySend(recordingId)
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
}
