package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.presentation.pods.detail.MetricsRange
import kotlin.math.roundToLong

@Composable
internal fun UsageChart(samples: List<PodMetricSample>, range: MetricsRange) {
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val spanMs = (samples.last().timestampMillis - samples.first().timestampMillis)
        .coerceAtLeast(1L)

    val cpuValues = samples.map { it.cpuCores }
    val memValues = samples.map { it.memoryBytes.toDouble() }
    val cpuMax = (cpuValues.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    val memMax = (memValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    val latest = samples.last()
    val description = "CPU and memory over the last ${range.label}. " +
            "Now ${formatCores(latest.cpuCores)} CPU and ${formatBytes(latest.memoryBytes)} memory."
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description },
    ) {
        // Paddings must be density-aware: raw floats clip axis labels on real
        // devices even though they look right in the preview.
        val padLeft = 52.dp.toPx()
        val padRight = 48.dp.toPx()
        val padTop = 8.dp.toPx()
        val padBottom = 30.dp.toPx()
        val labelAscent = 11.sp.toPx()
        val width = size.width - padLeft - padRight
        val height = size.height - padTop - padBottom
        val step = width / (samples.size - 1).coerceAtLeast(1)

        fun xAt(index: Int): Float = padLeft + step * index

        fun yAt(value: Double, max: Double): Float =
            padTop + height * (1f - (value / max).toFloat().coerceIn(0f, 1f))

        for (i in 0..2) {
            val fraction = i / 2f
            val gridLineY = padTop + height * (1f - fraction)
            drawLine(
                gridColor,
                Offset(padLeft, gridLineY),
                Offset(padLeft + width, gridLineY),
                strokeWidth = 1f
            )

            drawText(
                textMeasurer = textMeasurer,
                text = formatBytes((memMax * fraction).roundToLong()),
                topLeft = Offset(0f, gridLineY - labelAscent / 2),
                style = labelStyle,
            )
            drawText(
                textMeasurer = textMeasurer,
                text = formatCores(cpuMax * fraction),
                topLeft = Offset(padLeft + width + 6.dp.toPx(), gridLineY - labelAscent / 2),
                style = labelStyle,
            )
        }

        fun seriesPath(values: List<Double>, max: Double): Path {
            val path = Path()
            values.forEachIndexed { index, value ->
                val pointX = xAt(index)
                val pointY = yAt(value, max)
                if (index == 0) {
                    path.moveTo(pointX, pointY)
                } else {
                    val previousPointX = xAt(index - 1)
                    val previousPointY = yAt(values[index - 1], max)
                    val midControlX = (previousPointX + pointX) / 2f
                    path.cubicTo(midControlX, previousPointY, midControlX, pointY, pointX, pointY)
                }
            }
            return path
        }

        val cpuPath = seriesPath(cpuValues, cpuMax)
        val memPath = seriesPath(memValues, memMax)
        val bottom = padTop + height

        drawPath(
            brush = Brush.verticalGradient(
                colors = listOf(CPU_COLOR.copy(alpha = 0.22f), Color.Transparent),
                startY = padTop,
                endY = bottom,
            ),
            path = Path().apply {
                addPath(cpuPath)
                lineTo(xAt(samples.size - 1), bottom)
                lineTo(xAt(0), bottom)
                close()
            },
        )

        drawPath(cpuPath, CPU_COLOR, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(memPath, MEMORY_COLOR, style = Stroke(width = 4f, cap = StrokeCap.Round))

        val spanLabel = formatSpan(spanMs, range)
        drawText(
            textMeasurer = textMeasurer,
            text = "-$spanLabel",
            topLeft = Offset(padLeft, padTop + height + 8.dp.toPx()),
            style = labelStyle,
        )
        val nowLayout = textMeasurer.measure("now", labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = "now",
            topLeft = Offset(
                padLeft + width - nowLayout.size.width,
                padTop + height + 8.dp.toPx(),
            ),
            style = labelStyle,
        )
    }
}
