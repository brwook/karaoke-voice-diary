package com.konodiary.app.audio.analysis

import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.AudioAnalyzer
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.AnalysisState
import com.konodiary.app.core.model.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultAnalysisController(
    private val scope: CoroutineScope,
    private val recordingRepository: RecordingRepository,
    private val audioAnalyzer: AudioAnalyzer,
) : AnalysisController {

    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    override val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    private val mutex = Mutex()
    private val running = mutableSetOf<Long>()

    override fun startAnalysis(recordingId: Long) {
        scope.launch {
            // Ignore if already analyzing this recording.
            val started = mutex.withLock {
                if (running.contains(recordingId)) false else { running.add(recordingId); true }
            }
            if (!started) return@launch

            updateProgress(recordingId, 0f)
            try {
                val recording = recordingRepository.getRecording(recordingId) ?: return@launch
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
            } catch (t: Throwable) {
                runCatching { recordingRepository.setAnalysisState(recordingId, AnalysisState.FAILED) }
            } finally {
                mutex.withLock { running.remove(recordingId) }
                removeProgress(recordingId)
            }
        }
    }

    private fun updateProgress(id: Long, value: Float) {
        _progress.value = _progress.value.toMutableMap().apply { put(id, value) }
    }

    private fun removeProgress(id: Long) {
        _progress.value = _progress.value.toMutableMap().apply { remove(id) }
    }
}
