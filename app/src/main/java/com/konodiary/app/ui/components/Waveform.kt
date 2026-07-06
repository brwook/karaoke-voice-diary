package com.konodiary.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.konodiary.app.core.model.Envelope
import com.konodiary.app.core.model.Segment

/**
 * Draws the loudness envelope as vertical bars, with detected/manual segments
 * shown as a translucent color overlay. The envelope is down-sampled to the
 * canvas width (bucketed max) so an hour-long recording (~36k frames) does not
 * issue tens of thousands of draw calls per recomposition.
 */
@Composable
fun Waveform(
    envelope: Envelope?,
    color: Color,
    modifier: Modifier = Modifier,
    segments: List<Segment> = emptyList(),
    totalDurationMs: Long = 0L,
    segmentColor: Color = color,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Segment overlays first (behind the bars). Prefer the envelope's own span
        // if the caller could not supply a duration.
        val span = if (totalDurationMs > 0) {
            totalDurationMs
        } else {
            envelope?.let { it.data.size.toLong() * it.frameMs } ?: 0L
        }
        if (span > 0) {
            for (seg in segments) {
                val startX = (seg.startMs.toFloat() / span * w).coerceIn(0f, w)
                val endX = (seg.endMs.toFloat() / span * w).coerceIn(0f, w)
                val rectW = (endX - startX)
                if (rectW <= 0f) continue
                drawRect(
                    color = segmentColor.copy(alpha = 0.25f),
                    topLeft = Offset(startX, 0f),
                    size = Size(rectW, h),
                )
            }
        }

        val data = envelope?.data ?: return@Canvas
        if (data.isEmpty()) return@Canvas

        // Down-sample to at most one bar per canvas pixel using the bucket max.
        val bucketCount = w.toInt().coerceAtLeast(1).coerceAtMost(data.size)
        val step = w / bucketCount
        for (b in 0 until bucketCount) {
            val from = (b.toLong() * data.size / bucketCount).toInt()
            val to = ((b + 1).toLong() * data.size / bucketCount).toInt().coerceAtLeast(from + 1)
            var amp = 0f
            for (i in from until to) {
                val v = data[i]
                if (v > amp) amp = v
            }
            amp = amp.coerceIn(0f, 1f)
            val barHeight = amp * h
            val x = b * step
            drawLine(
                color = color,
                start = Offset(x, h),
                end = Offset(x, h - barHeight),
                strokeWidth = step.coerceAtLeast(1f),
            )
        }
    }
}
