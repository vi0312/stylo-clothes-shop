package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.feature.admin.model.RevenuePoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayPeriodPattern = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("vi-VN"))
private val monthPeriodPattern = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("vi-VN"))

@Composable
fun AdminRevenueChart(
    series: List<RevenuePoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chartDescription = stringResource(
        R.string.admin_reports_chart_description,
        series.joinToString(separator = "; ") { point ->
            context.getString(
                R.string.admin_reports_chart_point,
                chartPeriodLabel(point.period),
                Formatters.money(point.revenue),
                point.orders,
            )
        },
    )
    val barColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val peakLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.semantics { contentDescription = chartDescription }) {
        if (series.isEmpty()) return@Canvas

        val axisLabelSpace = 18.dp.toPx()
        val baseLine = size.height - axisLabelSpace
        val maxRevenue = series.maxOf { it.revenue }.coerceAtLeast(1L)
        // Keep the end bars clear of the platform scroll indicator hugging the right edge.
        val sideInset = 4.dp.toPx()
        val drawWidth = size.width - sideInset * 2
        val slot = drawWidth / series.size
        val barWidth = (slot * 0.55f)
            .coerceAtLeast(2.dp.toPx())
            .coerceAtMost(28.dp.toPx())
        val peakIndex = series.indices.maxBy { series[it].revenue }
        val peakLayout = if (series[peakIndex].revenue > 0) {
            textMeasurer.measure(
                text = AnnotatedString(Formatters.money(series[peakIndex].revenue)),
                style = peakLabelStyle,
            )
        } else {
            null
        }
        val plotTop = (peakLayout?.size?.height ?: 0).toFloat() + 4.dp.toPx()
        val plotHeight = baseLine - plotTop

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = baseLine - plotHeight * fraction
            drawLine(
                color = axisColor,
                start = Offset(sideInset, y),
                end = Offset(size.width - sideInset, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        series.forEachIndexed { index, point ->
            val barHeight = (point.revenue.toFloat() / maxRevenue) * (plotHeight - 2.dp.toPx())
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(
                        sideInset + index * slot + (slot - barWidth) / 2f,
                        baseLine - barHeight,
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }

        if (peakLayout != null) {
            val peakBarLeft = sideInset + peakIndex * slot + (slot - barWidth) / 2f
            val peakX = (peakBarLeft + barWidth - peakLayout.size.width)
                .coerceIn(sideInset, size.width - sideInset - peakLayout.size.width)
            drawText(peakLayout, topLeft = Offset(peakX, 0f))
        }

        val labelIndices = listOf(0, series.lastIndex / 2, series.lastIndex).distinct()
        labelIndices.forEach { index ->
            val layout = textMeasurer.measure(
                text = AnnotatedString(chartPeriodLabel(series[index].period)),
                style = labelStyle,
            )
            val x = when (index) {
                0 -> sideInset
                series.lastIndex -> size.width - sideInset - layout.size.width
                else -> sideInset + index * slot + (slot - layout.size.width) / 2f
            }
            drawText(layout, topLeft = Offset(x, baseLine + 2.dp.toPx()))
        }
    }
}

private fun chartPeriodLabel(period: String): String =
    if (period.length == 7) {
        YearMonth.parse(period).format(monthPeriodPattern)
    } else {
        LocalDate.parse(period).format(dayPeriodPattern)
    }
