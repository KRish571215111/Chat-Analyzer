package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.secondary
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    val maxVal = (data.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height

            val gridLines = 4
            val gridStep = height / (gridLines + 1)
            for (i in 1..gridLines) {
                val y = i * gridStep
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (data.isEmpty()) return@Canvas

            val barSpacing = 12.dp.toPx()
            val totalBarCount = data.size
            val availableWidth = width - (barSpacing * (totalBarCount + 1))
            val barWidth = availableWidth / totalBarCount

            data.forEachIndexed { index, pair ->
                val label = pair.first
                val value = pair.second

                val barHeight = (value / maxVal) * (height - 40.dp.toPx()) * animatedProgress.value
                val x = barSpacing + index * (barWidth + barSpacing)
                val y = height - 20.dp.toPx() - barHeight

                // Gradient brush for professional look
                val gradient = Brush.verticalGradient(
                    colors = listOf(barColor, accentColor)
                )

                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Label Text
                val textLayout = textMeasurer.measure(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x + (barWidth - textLayout.size.width) / 2,
                        height - 18.dp.toPx()
                    )
                )

                // Value Text on top of bar (if big enough)
                if (barHeight > 15.dp.toPx()) {
                    val valueLayout = textMeasurer.measure(
                        text = value.toInt().toString(),
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    )
                    drawText(
                        textLayoutResult = valueLayout,
                        topLeft = Offset(
                            x + (barWidth - valueLayout.size.width) / 2,
                            y + 2.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleDonutChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer
    )
) {
    val total = data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(130.dp)
                .weight(1f)
        ) {
            val canvasSize = size.minDimension
            val strokeWidth = 16.dp.toPx()
            val radius = (canvasSize - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            var startAngle = -90f

            data.forEachIndexed { index, pair ->
                val sweepAngle = (pair.second / total) * 360f * animatedProgress.value
                val color = colors[index % colors.size]

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )

                startAngle += sweepAngle
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.Center
        ) {
            data.forEachIndexed { index, pair ->
                val color = colors[index % colors.size]
                val percentage = (pair.second / total * 100).toInt()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, shape = RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${pair.first} ($percentage%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityHeatmap(
    activityDays: Map<Int, Int>, // DayOfWeek (1..7) to messageCount
    modifier: Modifier = Modifier
) {
    val maxCount = (activityDays.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Activity Heatmap (By Weekday)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayNames.forEachIndexed { index, day ->
                val count = activityDays[index + 1] ?: 0
                val intensity = if (count > 0) {
                    (count.toFloat() / maxCount).coerceIn(0.1f, 1f)
                } else 0f

                val boxColor = if (count > 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = intensity)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(boxColor, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityLineChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    areaGradientColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    val maxVal = (data.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx()
            val topPadding = 16.dp.toPx()
            val usableHeight = height - bottomPadding - topPadding

            // Draw grid lines
            val gridLines = 4
            val gridStep = usableHeight / gridLines
            for (i in 0..gridLines) {
                val y = topPadding + i * gridStep
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.15f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (data.isEmpty()) return@Canvas

            // Compute coordinates
            val pointSpacing = width / (data.size - 1).coerceAtLeast(1)
            val points = data.mapIndexed { index, pair ->
                val x = index * pointSpacing
                val y = height - bottomPadding - ((pair.second / maxVal) * usableHeight * animatedProgress.value)
                Offset(x, y)
            }

            // Draw area gradient under the curve
            if (points.size > 1) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, height - bottomPadding)
                    // Draw smooth bezier curve
                    lineTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val controlX = (prev.x + curr.x) / 2
                        cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                    }
                    lineTo(points.last().x, height - bottomPadding)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(areaGradientColor, Color.Transparent),
                        startY = topPadding,
                        endY = height - bottomPadding
                    )
                )

                // Draw curve line
                val linePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val controlX = (prev.x + curr.x) / 2
                        cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )

                // Draw glowing dots at points
                points.forEachIndexed { idx, point ->
                    if (idx % (data.size / 5).coerceAtLeast(1) == 0 || idx == data.size - 1) {
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = point
                        )
                    }
                }
            }

            // Draw X-axis labels
            data.forEachIndexed { index, pair ->
                if (index % (data.size / 5).coerceAtLeast(1) == 0 || index == data.size - 1) {
                    val label = pair.first
                    val textLayout = textMeasurer.measure(
                        text = label,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            (index * pointSpacing - textLayout.size.width / 2).coerceIn(0f, width - textLayout.size.width),
                            height - 18.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}
