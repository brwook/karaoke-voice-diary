package com.konodiary.app.ui.common

/**
 * 분석 처리 속도(오디오ms / 실제 경과ms)를 지수이동평균(EMA)으로 추정.
 *
 * 순수 Kotlin — android import 금지. 스레드 안전성은 호출측 책임(단일 스레드/컴포저블에서 호출).
 */
class EtaEstimator(private val alpha: Double = 0.3) {

    // 현재 배속 추정치. 아직 유효 샘플이 없으면 null.
    private var speed: Double? = null

    /**
     * 관측 샘플 추가.
     *
     * - [wallDeltaMs] <= 0 또는 [audioDeltaMs] < 0 이면 무시(측정 오류/역행 방지).
     * - audioDeltaMs == 0 샘플도 유효(속도 하락을 반영)하되, 아직 추정치가 없을 때
     *   첫 샘플이 0이면 무시한다(0으로 초기화되어 이후 EMA가 0에 갇히는 것을 방지).
     */
    fun addSample(audioDeltaMs: Long, wallDeltaMs: Long) {
        if (wallDeltaMs <= 0 || audioDeltaMs < 0) return
        val observed = audioDeltaMs.toDouble() / wallDeltaMs.toDouble()
        val current = speed
        if (current == null) {
            // 첫 유효 샘플이 0이면 초기화하지 않는다.
            if (observed == 0.0) return
            speed = observed
        } else {
            speed = alpha * observed + (1 - alpha) * current
        }
    }

    /** 현재 추정 배속 (예: 30.0 = 실시간의 30배). 샘플 없으면 null. */
    val speedFactor: Double?
        get() = speed

    /**
     * [remainingAudioMs]를 처리하는 데 걸릴 예상 실제 시간(ms). 속도 미상이면 null.
     * remainingAudioMs == 0이면 0을 반환.
     */
    fun etaMs(remainingAudioMs: Long): Long? {
        val s = speed ?: return null
        if (remainingAudioMs <= 0L) return 0L
        if (s <= 0.0) return null
        return (remainingAudioMs / s).toLong()
    }
}
