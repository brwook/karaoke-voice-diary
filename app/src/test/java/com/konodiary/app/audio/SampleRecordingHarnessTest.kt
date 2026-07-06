package com.konodiary.app.audio

import com.konodiary.app.audio.analysis.KaraokeSegmenter
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 실제 녹음 파일(WAV로 디코딩된 것)에 진짜 [KaraokeSegmenter]를 돌려 보는
 * 진단 하네스. 평상시 테스트 런에서는 no-op이며,
 * `.\gradlew.bat :app:testDebugUnitTest -PsampleDir=<wav 디렉토리>` 로 실행한다.
 *
 * dB 프레임 계산은 EnergyAudioAnalyzer와 동일한 수식을 사용한다
 * (100ms RMS -> 20*log10, 바닥 -120dB, 다채널은 평균 믹스다운).
 */
class SampleRecordingHarnessTest {

    @Test
    fun analyzeSampleRecordings() {
        val dir = System.getProperty("sampleDir").orEmpty()
        if (dir.isBlank()) return // 샘플 경로가 없으면 그냥 통과

        val wavs = File(dir).listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            ?.sortedBy { it.name }.orEmpty()
        check(wavs.isNotEmpty()) { "sampleDir에 wav 파일이 없음: $dir" }

        val report = StringBuilder()
        for (wav in wavs) {
            report.appendLine(analyzeOne(wav))
        }

        val out = File(dir, "analysis-report.txt")
        out.writeText(report.toString())
        println(report)
        println("[harness] report written: ${out.absolutePath}")
    }

    private fun analyzeOne(wav: File): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(72))
        sb.appendLine("FILE: ${wav.name} (${wav.length() / 1024 / 1024}MB)")

        val frameDb = computeFrameDb(wav)
        val frameMs = FRAME_MS
        val totalMs = frameDb.size.toLong() * frameMs
        sb.appendLine("duration=${fmt(totalMs)}  frames=${frameDb.size}")

        // --- KaraokeSegmenter와 동일한 전처리로 진단 통계 ---
        val p = KaraokeSegmenter.Params()
        val smoothed = KaraokeSegmenter.movingAverage(
            frameDb, (p.smoothingWindowMs / frameMs).coerceAtLeast(1)
        )
        val noiseFloor = percentile(smoothed, p.noiseFloorPercentile)
        val p95 = percentile(smoothed, p.musicPercentile)
        val range = p95 - noiseFloor
        val threshold = noiseFloor + p.thresholdFraction * range

        sb.appendLine(
            "smoothed dB: min=%.1f p05=%.1f p20(noiseFloor)=%.1f p50=%.1f p80=%.1f p95=%.1f max=%.1f".format(
                smoothed.min(), percentile(smoothed, 0.05), noiseFloor,
                percentile(smoothed, 0.50), percentile(smoothed, 0.80), p95, smoothed.max()
            )
        )
        sb.appendLine(
            "uniform check: p95-p20 = %.1f dB (%s, 기준 %.1f)  threshold=%.1f".format(
                range, if (range < p.uniformRangeDb) "!!UNIFORM -> 전체 1구간!!" else "OK", p.uniformRangeDb, threshold
            )
        )

        // dB 분포 히스토그램 (2dB 빈) — 노이즈 플로어/음악 모드의 이봉성 진단용
        sb.appendLine("histogram(smoothed dB, 2dB bins):")
        val lo = (smoothed.min() / 2f).toInt() * 2 - 2
        val hi = (smoothed.max() / 2f).toInt() * 2 + 2
        val bins = IntArray(((hi - lo) / 2) + 1)
        for (v in smoothed) bins[((v - lo) / 2f).toInt().coerceIn(0, bins.size - 1)]++
        for (b in bins.indices) {
            val pct = 100.0 * bins[b] / smoothed.size
            if (pct >= 0.3) sb.appendLine(
                "  %5d..%ddB %5.1f%% %s".format(lo + b * 2, lo + b * 2 + 2, pct, "#".repeat((pct / 2).toInt() + 1))
            )
        }

        // 모폴로지 이전의 raw 활성 구간/갭 구조 (갭 메움·최소 길이 파라미터 적합성 진단용)
        val active = BooleanArray(smoothed.size) { smoothed[it] > threshold }
        sb.appendLine("active fraction = %.1f%%".format(100.0 * active.count { it } / active.size))
        val rawRuns = runsOf(active)
        val longRuns = rawRuns.filter { (it.last - it.first + 1) * frameMs >= 10_000 }
        sb.appendLine("raw active runs(>=10s): ${longRuns.size}개")
        for (r in longRuns) {
            val startMs = r.first.toLong() * frameMs
            val lenMs = (r.last - r.first + 1).toLong() * frameMs
            sb.appendLine("   @${fmt(startMs)}  len=${fmt(lenMs)}")
        }
        val gaps = rawRuns.zipWithNext { a, b -> (b.first - a.last - 1).toLong() * frameMs }
            .filter { it >= 2_000 }
        sb.appendLine("raw gaps(>=2s): " + gaps.joinToString(", ") { fmt(it) })

        // --- 실제 세그멘터 결과 (기본 파라미터) ---
        val segs = KaraokeSegmenter.segment(frameDb, frameMs)
        sb.appendLine("-".repeat(72))
        sb.appendLine("[기본 파라미터] 검출 구간 ${segs.size}개:")
        segs.forEachIndexed { i, s ->
            sb.appendLine(
                "  #%-2d %s ~ %s  (len=%s, conf=%.2f)".format(
                    i + 1, fmt(s.startMs), fmt(s.endMs), fmt(s.endMs - s.startMs), s.confidence
                )
            )
        }

        // --- 파라미터 민감도 ---
        sb.appendLine("-".repeat(72))
        sb.appendLine("[민감도] 파라미터 변형별 구간 수:")
        val variants = listOf(
            "gapFill 8s" to p.copy(maxGapFillMs = 8_000),
            "gapFill 20s" to p.copy(maxGapFillMs = 20_000),
            "minSeg 40s" to p.copy(minSegmentMs = 40_000),
            "minSeg 70s" to p.copy(minSegmentMs = 70_000),
            "threshold 0.25" to p.copy(thresholdFraction = 0.25),
            "threshold 0.45" to p.copy(thresholdFraction = 0.45),
            "floor p05" to p.copy(noiseFloorPercentile = 0.05),
            "floor p10" to p.copy(noiseFloorPercentile = 0.10),
            "floor p05 th.45" to p.copy(noiseFloorPercentile = 0.05, thresholdFraction = 0.45),
            "floor p05 th.50" to p.copy(noiseFloorPercentile = 0.05, thresholdFraction = 0.50),
            "p05 th.45 gap20" to p.copy(
                noiseFloorPercentile = 0.05, thresholdFraction = 0.45, maxGapFillMs = 20_000
            ),
        )
        for ((name, vp) in variants) {
            val vs = KaraokeSegmenter.segment(frameDb, frameMs, vp)
            sb.appendLine(
                "  %-15s -> %d개  [%s]".format(
                    name, vs.size, vs.joinToString(" / ") { "${fmt(it.startMs)}~${fmt(it.endMs)}" }
                )
            )
        }
        return sb.toString()
    }

    // ---- EnergyAudioAnalyzer와 동일한 프레임 dB 계산 (WAV PCM 16bit 입력) ----

    private fun computeFrameDb(wav: File): FloatArray {
        DataInputStream(FileInputStream(wav).buffered(1 shl 20)).use { ins ->
            val header = WavHeader.read(ins)
            check(header.bitsPerSample == 16 && header.audioFormat == 1) {
                "PCM 16-bit WAV만 지원: ${wav.name} (format=${header.audioFormat}, bits=${header.bitsPerSample})"
            }
            val channels = header.channels
            val samplesPerFrame = (header.sampleRate * FRAME_MS / 1000).coerceAtLeast(1)

            val frameDb = ArrayList<Float>(1 shl 16)
            var sumSq = 0.0
            var count = 0

            val bytesPerSampleFrame = 2 * channels
            val buf = ByteArray(bytesPerSampleFrame * 4096)
            var remaining = header.dataSize
            while (remaining > 0) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val n = ins.read(buf, 0, toRead)
                if (n <= 0) break
                remaining -= n
                var off = 0
                while (off + bytesPerSampleFrame <= n) {
                    var acc = 0f
                    for (c in 0 until channels) {
                        val lo = buf[off + 2 * c].toInt() and 0xFF
                        val hi = buf[off + 2 * c + 1].toInt()
                        acc += ((hi shl 8) or lo) / 32768f
                    }
                    val v = acc / channels
                    sumSq += v.toDouble() * v
                    count++
                    if (count >= samplesPerFrame) {
                        frameDb.add(rmsToDb(sumSq, count))
                        sumSq = 0.0
                        count = 0
                    }
                    off += bytesPerSampleFrame
                }
            }
            if (count > 0) frameDb.add(rmsToDb(sumSq, count))
            return frameDb.toFloatArray()
        }
    }

    private fun rmsToDb(sumSq: Double, count: Int): Float {
        if (count == 0) return SILENCE_DB
        val rms = sqrt(sumSq / count)
        if (rms <= 1e-7) return SILENCE_DB
        return (20.0 * log10(rms)).toFloat().coerceAtLeast(SILENCE_DB)
    }

    private class WavHeader(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataSize: Long,
    ) {
        companion object {
            /** RIFF 청크를 순회해 fmt/data를 찾는다. data 청크 시작 위치에서 반환. */
            fun read(ins: DataInputStream): WavHeader {
                fun u16(): Int {
                    val a = ins.readUnsignedByte(); val b = ins.readUnsignedByte()
                    return (b shl 8) or a
                }
                fun u32(): Long {
                    val a = u16().toLong(); val b = u16().toLong()
                    return (b shl 16) or a
                }
                fun tag(): String {
                    val b = ByteArray(4); ins.readFully(b); return String(b, Charsets.US_ASCII)
                }
                check(tag() == "RIFF") { "RIFF 아님" }
                u32() // riff size
                check(tag() == "WAVE") { "WAVE 아님" }
                var audioFormat = -1; var channels = -1; var sampleRate = -1; var bits = -1
                while (true) {
                    val id = tag()
                    val size = u32()
                    when (id) {
                        "fmt " -> {
                            audioFormat = u16(); channels = u16()
                            sampleRate = u32().toInt(); u32(); u16()
                            bits = u16()
                            var skip = size - 16
                            while (skip-- > 0) ins.readUnsignedByte()
                        }
                        "data" -> {
                            check(audioFormat != -1) { "fmt 청크가 data보다 뒤에 있음" }
                            return WavHeader(audioFormat, channels, sampleRate, bits, size)
                        }
                        else -> { var skip = size + (size and 1); while (skip-- > 0) ins.readUnsignedByte() }
                    }
                }
            }
        }
    }

    // ---- KaraokeSegmenter.percentile(private) 복제: nearest-rank ----
    private fun percentile(values: FloatArray, p: Double): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf().also { it.sort() }
        val idx = (p * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun runsOf(active: BooleanArray): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var start = -1
        for (i in active.indices) {
            if (active[i] && start == -1) start = i
            else if (!active[i] && start != -1) { runs.add(start until i); start = -1 }
        }
        if (start != -1) runs.add(start until active.size)
        return runs
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    private companion object {
        const val FRAME_MS = 100
        const val SILENCE_DB = -120f
    }
}
