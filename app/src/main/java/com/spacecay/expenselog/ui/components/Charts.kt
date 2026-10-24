package com.spacecay.expenselog.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DonutSlice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTop: String,
    centerBottom: String,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
) {
    val anim = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { anim.animateTo(1f, tween(700)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            var start = -90f
            val gap = if (slices.size > 1) 1.6f else 0f
            slices.forEach { s ->
                val sweep = 360f * (s.value / total) * anim.value
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerBottom, style = MaterialTheme.typography.titleLarge)
        }
    }
}

data class BarPoint(val label: String, val a: Long, val b: Long = 0)

/**
 * Grouped bar chart. [a] uses [colorA], [b] uses [colorB] (set showB=false for single series).
 */
@Composable
fun BarChart(
    points: List<BarPoint>,
    colorA: Color,
    colorB: Color,
    showB: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    labelEvery: Int = 1,
    highlight: Int = -1,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val anim = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { anim.animateTo(1f, tween(600)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val labelH = 18.dp.toPx()
        val chartH = this.size.height - labelH
        val max = points.maxOf { maxOf(it.a, if (showB) it.b else 0L) }.coerceAtLeast(1L).toFloat()
        // grid lines
        for (i in 0..3) {
            val y = chartH * i / 3f
            drawLine(grid, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 1f)
        }
        val slot = this.size.width / points.size
        val groupW = slot * 0.62f
        val barW = if (showB) groupW / 2f else groupW
        val r = CornerRadius(minOf(barW / 2, 6.dp.toPx()))
        points.forEachIndexed { i, p ->
            val x0 = i * slot + (slot - groupW) / 2
            val alpha = if (highlight >= 0 && highlight != i) 0.45f else 1f
            val ha = chartH * (p.a / max) * anim.value
            if (p.a > 0) drawRoundRect(colorA.copy(alpha = alpha), Offset(x0, chartH - ha), Size(barW, ha), r)
            if (showB) {
                val hb = chartH * (p.b / max) * anim.value
                if (p.b > 0) drawRoundRect(colorB.copy(alpha = alpha), Offset(x0 + barW, chartH - hb), Size(barW, hb), r)
            }
            if (i % labelEvery == 0 || i == highlight) {
                val tl = measurer.measure(p.label, TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(tl, topLeft = Offset(i * slot + slot / 2 - tl.size.width / 2, chartH + 4.dp.toPx()))
            }
        }
    }
}

/** Simple line for balance trends. */
@Composable
fun LineChart(values: List<Long>, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val anim = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { anim.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val min = values.min().toFloat()
        val max = values.max().toFloat()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = this.size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, this.size.height - (this.size.height * 0.9f) * ((v - min) / span) - this.size.height * 0.05f) }
        val n = (pts.size * anim.value).toInt().coerceAtLeast(2).coerceAtMost(pts.size)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until n) lineTo(pts[i].x, pts[i].y)
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(pts[n - 1].x, this@Canvas.size.height)
            lineTo(pts[0].x, this@Canvas.size.height)
            close()
        }
        drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
