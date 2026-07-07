package com.konodiary.app.ui

import com.konodiary.app.ui.common.EtaEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaEstimatorTest {

    @Test
    fun `no samples yields null speed and eta`() {
        val est = EtaEstimator()
        assertNull(est.speedFactor)
        assertNull(est.etaMs(10_000L))
    }

    @Test
    fun `single sample sets speedFactor to observed ratio`() {
        val est = EtaEstimator()
        // 30초 오디오를 1초 실측에 처리 = 30배속.
        est.addSample(audioDeltaMs = 30_000L, wallDeltaMs = 1_000L)
        assertNotNull(est.speedFactor)
        assertEquals(30.0, est.speedFactor!!, 1e-9)
    }

    @Test
    fun `two samples converge via EMA`() {
        val est = EtaEstimator(alpha = 0.3)
        est.addSample(30_000L, 1_000L) // speed = 30
        est.addSample(10_000L, 1_000L) // observed = 10
        // EMA = 0.3*10 + 0.7*30 = 24
        assertEquals(24.0, est.speedFactor!!, 1e-9)
    }

    @Test
    fun `invalid samples are ignored`() {
        val est = EtaEstimator()
        est.addSample(30_000L, 0L)       // wallDelta <= 0 → 무시
        est.addSample(30_000L, -5L)      // wallDelta < 0 → 무시
        est.addSample(-1L, 1_000L)       // audioDelta < 0 → 무시
        assertNull(est.speedFactor)
    }

    @Test
    fun `first zero-audio sample is ignored to avoid zero init`() {
        val est = EtaEstimator()
        est.addSample(0L, 1_000L) // 첫 샘플이 0 → 무시
        assertNull(est.speedFactor)
        // 이후 정상 샘플은 반영된다.
        est.addSample(30_000L, 1_000L)
        assertEquals(30.0, est.speedFactor!!, 1e-9)
    }

    @Test
    fun `zero-audio sample after valid sample lowers speed`() {
        val est = EtaEstimator(alpha = 0.5)
        est.addSample(30_000L, 1_000L) // speed = 30
        est.addSample(0L, 1_000L)      // observed = 0 → EMA = 0.5*0 + 0.5*30 = 15
        assertEquals(15.0, est.speedFactor!!, 1e-9)
    }

    @Test
    fun `etaMs computes remaining wall time from speed`() {
        val est = EtaEstimator()
        est.addSample(30_000L, 1_000L) // 30배속
        // 남은 오디오 300초 → 실측 10초 = 10_000ms.
        assertEquals(10_000L, est.etaMs(300_000L))
    }

    @Test
    fun `etaMs of zero remaining is zero`() {
        val est = EtaEstimator()
        est.addSample(30_000L, 1_000L)
        assertEquals(0L, est.etaMs(0L))
    }

    @Test
    fun `etaMs is null when speed unknown`() {
        val est = EtaEstimator()
        assertNull(est.etaMs(0L))
        assertNull(est.etaMs(100_000L))
    }

    @Test
    fun `etaMs of negative remaining is zero`() {
        val est = EtaEstimator()
        est.addSample(30_000L, 1_000L)
        assertEquals(0L, est.etaMs(-500L))
    }

    @Test
    fun `speed stays positive across many mixed samples`() {
        val est = EtaEstimator()
        est.addSample(20_000L, 1_000L)
        est.addSample(0L, 1_000L)
        est.addSample(40_000L, 1_000L)
        assertTrue(est.speedFactor!! > 0.0)
    }
}
