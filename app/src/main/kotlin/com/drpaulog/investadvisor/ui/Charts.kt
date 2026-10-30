package com.drpaulog.investadvisor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drpaulog.investadvisor.core.ForecastBand

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1 - ((v - min) / range)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Gráfico de preços com média móvel opcional e, à direita, o leque de previsão
 * (intervalo 10%–90% e mediana).
 */
@Composable
fun PriceChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    movingAverage: List<Double?>? = null,
    forecast: List<ForecastBand>? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val maColor = MaterialTheme.colorScheme.tertiary
    val fanColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val fc = forecast?.takeIf { it.size >= 2 }
        val all = buildList {
            addAll(values)
            movingAverage?.filterNotNull()?.let { addAll(it) }
            fc?.forEach { add(it.p10); add(it.p90) }
        }
        val min = all.min()
        val max = all.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val padTop = 8.dp.toPx()
        val padBottom = 8.dp.toPx()
        val h = size.height - padTop - padBottom
        fun y(v: Double) = padTop + h * (1 - ((v - min) / range)).toFloat()
        val histWidth = if (fc != null) size.width * 0.7f else size.width
        fun xHist(i: Int) = histWidth * i / (values.size - 1)

        drawGrid(min, max, gridColor, labelColor, measurer, ::y)

        movingAverage?.let { ma ->
            val path = Path()
            var started = false
            ma.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                if (!started) { path.moveTo(xHist(i), y(v)); started = true } else path.lineTo(xHist(i), y(v))
            }
            drawPath(path, maColor, style = Stroke(width = 1.5f.dp.toPx()))
        }

        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(xHist(i), y(v)) else path.lineTo(xHist(i), y(v)) }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

        if (fc != null) {
            val fanWidth = size.width - histWidth
            val total = fc.last().months.coerceAtLeast(1)
            fun xF(b: ForecastBand) = histWidth + fanWidth * b.months / total
            val area = Path().apply {
                fc.forEachIndexed { i, b -> if (i == 0) moveTo(xF(b), y(b.p90)) else lineTo(xF(b), y(b.p90)) }
                fc.asReversed().forEach { b -> lineTo(xF(b), y(b.p10)) }
                close()
            }
            drawPath(area, fanColor.copy(alpha = 0.22f))
            val median = Path().apply {
                fc.forEachIndexed { i, b -> if (i == 0) moveTo(xF(b), y(b.p50)) else lineTo(xF(b), y(b.p50)) }
            }
            drawPath(
                median, fanColor,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
            drawLine(gridColor, Offset(histWidth, 0f), Offset(histWidth, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

/** Leque da evolução prevista de uma carteira ao longo do prazo. */
@Composable
fun FanChart(path: List<ForecastBand>, initial: Double, modifier: Modifier = Modifier) {
    val fanColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (path.size < 2) return@Canvas
        val min = minOf(path.minOf { it.p10 }, initial)
        val max = path.maxOf { it.p90 }
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val padTop = 8.dp.toPx()
        val h = size.height - padTop * 2
        fun y(v: Double) = padTop + h * (1 - ((v - min) / range)).toFloat()
        val total = path.last().months.coerceAtLeast(1)
        fun x(b: ForecastBand) = size.width * b.months / total

        drawGrid(min, max, gridColor, labelColor, measurer, ::y, money = true)
        drawLine(
            labelColor, Offset(0f, y(initial)), Offset(size.width, y(initial)), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        val area = Path().apply {
            path.forEachIndexed { i, b -> if (i == 0) moveTo(x(b), y(b.p90)) else lineTo(x(b), y(b.p90)) }
            path.asReversed().forEach { b -> lineTo(x(b), y(b.p10)) }
            close()
        }
        drawPath(area, fanColor.copy(alpha = 0.2f))
        val median = Path().apply {
            path.forEachIndexed { i, b -> if (i == 0) moveTo(x(b), y(b.p50)) else lineTo(x(b), y(b.p50)) }
        }
        drawPath(median, fanColor, style = Stroke(width = 2.5f.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
fun AllocationDonut(weights: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        var start = -90f
        val total = weights.sum().takeIf { it > 0 } ?: return@Canvas
        weights.forEachIndexed { i, w ->
            val sweep = (360 * w / total).toFloat()
            drawArc(
                color = SliceColors[i % SliceColors.size],
                startAngle = start,
                sweepAngle = (sweep - 1f).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

private fun DrawScope.drawGrid(
    min: Double,
    max: Double,
    gridColor: Color,
    labelColor: Color,
    measurer: TextMeasurer,
    y: (Double) -> Float,
    money: Boolean = false,
) {
    val style = TextStyle(color = labelColor, fontSize = 10.sp)
    for (k in 0..3) {
        val v = min + (max - min) * k / 3
        val yy = y(v)
        drawLine(gridColor, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
        val label = if (money) euros(v) else compactNumber(v)
        val layout = measurer.measure(label, style)
        val ty = (yy - layout.size.height - 2f).coerceAtLeast(0f)
        drawText(layout, topLeft = Offset(4f, ty))
    }
}

private fun compactNumber(v: Double): String = when {
    v >= 100_000 -> "%,.0f".format(PT, v)
    v >= 100 -> "%,.1f".format(PT, v)
    v >= 1 -> "%.2f".format(PT, v)
    else -> "%.4f".format(PT, v)
}
