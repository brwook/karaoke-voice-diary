package com.konodiary.app.audio.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.konodiary.app.core.contracts.AudioAnalyzer
import com.konodiary.app.core.model.AnalysisResult
import com.konodiary.app.core.model.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Streams the audio file through MediaCodec, computes a 100ms-frame loudness
 * curve (never holding full PCM in memory), then delegates the actual splitting
 * to [KaraokeSegmenter]. See docs/SPEC.md §5.
 */
class EnergyAudioAnalyzer(private val context: Context) : AudioAnalyzer {

    override suspend fun analyze(
        fileUri: String,
        onProgress: (Float) -> Unit,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(fileUri), null)
            val trackIndex = selectAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track in $fileUri" }
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            // Input-format values are only a fallback. The decoder confirms the
            // real output PCM format (encoding / channel count / sample rate) via
            // INFO_OUTPUT_FORMAT_CHANGED before the first output buffer; we update
            // these from codec.outputFormat when that event fires (see below).
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmEncoding =
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                }

            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }

            var samplesPerFrame = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
            val frameDb = ArrayList<Float>(4096)

            // Rolling accumulator across output buffers for the current 100ms frame.
            var sumSq = 0.0
            var sampleCount = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var lastPtsUs = 0L

            while (!sawOutputEos) {
                coroutineContext.ensureActive()

                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0,
                            )
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // The decoder has now finalized the real output PCM format.
                        // Adopt its encoding / channel count / sample rate so sample
                        // parsing and frame boundaries match what it actually emits.
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount =
                                outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        samplesPerFrame = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
                    } else if (outIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)

                            lastPtsUs = bufferInfo.presentationTimeUs
                            val monoSamples = readMonoSamples(outBuf, channelCount, pcmEncoding)
                            for (v in monoSamples) {
                                sumSq += v.toDouble() * v
                                sampleCount++
                                if (sampleCount >= samplesPerFrame) {
                                    frameDb.add(rmsToDb(sumSq, sampleCount))
                                    sumSq = 0.0
                                    sampleCount = 0
                                }
                            }

                            if (durationUs > 0) {
                                onProgress((lastPtsUs.toFloat() / durationUs).coerceIn(0f, 1f))
                            }
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                    // INFO_OUTPUT_BUFFERS_CHANGED (deprecated) falls through here.
                    if (sawOutputEos) break
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            // Flush a trailing partial frame.
            if (sampleCount > 0) frameDb.add(rmsToDb(sumSq, sampleCount))

            onProgress(1f)

            val dbArray = frameDb.toFloatArray()
            val segments = KaraokeSegmenter.segment(dbArray, FRAME_MS)
            val durationMs = maxOf(durationUs / 1000, dbArray.size.toLong() * FRAME_MS)
            // Envelope is the *smoothed* dB curve (2s moving average), per SPEC §5.5,
            // so the waveform is a loudness curve rather than noisy per-frame dB.
            val smoothedDb = KaraokeSegmenter.movingAverage(
                dbArray,
                (SMOOTHING_WINDOW_MS / FRAME_MS).coerceAtLeast(1),
            )
            val envelope = buildEnvelope(smoothedDb)

            AnalysisResult(
                recordingId = 0L, // filled in by the controller before saving
                durationMs = durationMs,
                segments = segments,
                envelope = Envelope(recordingId = 0L, frameMs = FRAME_MS, data = envelope),
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    /** Reads a byte buffer of interleaved PCM and returns mono samples in -1f..1f. */
    private fun readMonoSamples(buffer: ByteBuffer, channels: Int, pcmEncoding: Int): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = buffer.asFloatBuffer()
                val total = fb.remaining()
                val frames = total / channels
                FloatArray(frames) { f ->
                    var acc = 0f
                    for (c in 0 until channels) acc += fb.get(f * channels + c)
                    acc / channels
                }
            }
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                val total = buffer.remaining()
                val frames = total / channels
                FloatArray(frames) { f ->
                    var acc = 0f
                    for (c in 0 until channels) {
                        // 8-bit PCM is unsigned, centered at 128.
                        val s = (buffer.get(buffer.position() + f * channels + c).toInt() and 0xFF) - 128
                        acc += s / 128f
                    }
                    acc / channels
                }
            }
            else -> { // ENCODING_PCM_16BIT (default)
                val sb = buffer.asShortBuffer()
                val total = sb.remaining()
                val frames = total / channels
                FloatArray(frames) { f ->
                    var acc = 0f
                    for (c in 0 until channels) acc += sb.get(f * channels + c) / 32768f
                    acc / channels
                }
            }
        }
    }

    private fun rmsToDb(sumSq: Double, count: Int): Float {
        if (count == 0) return SILENCE_DB
        val rms = sqrt(sumSq / count)
        if (rms <= 1e-7) return SILENCE_DB
        return (20.0 * log10(rms)).toFloat().coerceAtLeast(SILENCE_DB)
    }

    /** Normalizes the (smoothed) dB curve to 0..1 over noiseFloor..max for the waveform. */
    private fun buildEnvelope(frameDb: FloatArray): FloatArray {
        if (frameDb.isEmpty()) return FloatArray(0)
        val sorted = frameDb.copyOf().also { it.sort() }
        val noiseFloor = sorted[(0.20 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)]
        val max = sorted.last()
        val range = (max - noiseFloor)
        return FloatArray(frameDb.size) { i ->
            if (range <= 0.0001f) 0f else ((frameDb[i] - noiseFloor) / range).coerceIn(0f, 1f)
        }
    }

    companion object {
        const val FRAME_MS = 100
        private const val TIMEOUT_US = 10_000L
        private const val SILENCE_DB = -120f

        /** Waveform smoothing window; matches KaraokeSegmenter.Params default. */
        private const val SMOOTHING_WINDOW_MS = 2_000
    }
}
