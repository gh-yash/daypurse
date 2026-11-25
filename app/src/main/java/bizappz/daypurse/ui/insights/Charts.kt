package bizappz.daypurse.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ring chart; [slices] are (colour, fraction) with fractions summing to ~1. */
@Composable
fun DonutChart(slices: List<Pair<Color, Float>>, trackColor: Color, modifier: Modifier = Modifier, thickness: Dp = 22.dp) {
    Canvas(modifier.aspectRatio(1f)) {
        val stroke = thickness.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val gap = if (slices.size > 1) 2f else 0f
        var start = -90f
        slices.forEach { (color, fraction) ->
            val sweep = 360f * fraction
            if (sweep > gap) {
                drawArc(color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            start += sweep
        }
    }
}

/**
 * Daily spend bars with a dashed line at [baseline] (the even daily split). Bars above the
 * baseline use [overColor].
 */
@Composable
fun DailyBarChart(
    values: List<Long>,
    baseline: Long,
    barColor: Color,
    overColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = maxOf(values.max(), baseline, 1L).toFloat() * 1.1f
        val slot = size.width / values.size
        val barWidth = (slot * 0.65f).coerceAtLeast(1f)
        val radius = CornerRadius(minOf(barWidth / 2, 6.dp.toPx()))
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val h = size.height * (v / max)
            drawRoundRect(
                color = if (v > baseline) overColor else barColor,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
        if (baseline > 0) {
            val y = size.height - size.height * (baseline / max)
            drawLine(
                lineColor, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
    }
}
