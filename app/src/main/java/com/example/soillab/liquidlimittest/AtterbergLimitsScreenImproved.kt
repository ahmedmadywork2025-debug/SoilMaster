package com.example.soillab.liquidlimittest

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.PathEffect as ComposePathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Green500
import com.example.soillab.ui.theme.Yellow500
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import com.example.soillab.ui.components.TestInfoSection

@Composable
fun AtterbergLimitsScreenImproved(
    viewModel: AtterbergCoreViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(reportIdToLoad) {
        viewModel.loadReportForEditing(reportIdToLoad)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AtterbergLimitsSetupSection(viewModel, uiState)
        AtterbergLimitsDataAndResultsSection(viewModel, uiState)
    }
}

@Composable
fun EnhancedLLSampleInputSection(samples: List<LiquidLimitSample>, validationResults: Map<Int, ValidationResult>, onValueChange: (Int, LiquidLimitSample) -> Unit, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        samples.forEach { sample ->
            val validation = validationResults[sample.id]
            val borderColor = when {
                validation == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                validation.isValid -> Green500
                else -> MaterialTheme.colorScheme.error
            }
            Column(
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sample_id, sample.id), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    ValidationIndicator(validation)
                    if (samples.size > 1) {
                        IconButton(onClick = { onRemove(sample.id) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuralInput(sample.blows, { onValueChange(sample.id, sample.copy(blows = it)) }, stringResource(R.string.blows), Modifier.weight(1f), isError = validation?.errors?.any { it.field == "blows" } ?: false)
                    NeuralInput(sample.waterContent, { onValueChange(sample.id, sample.copy(waterContent = it)) }, stringResource(R.string.water_content_percent), Modifier.weight(1.5f), isError = validation?.errors?.any { it.field == "waterContent" } ?: false)
                }
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Add, stringResource(R.string.add_ll_sample), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_ll_sample), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PLSampleInputSection(samples: List<PlasticLimitSample>, validationResults: Map<Int, ValidationResult>, onValueChange: (Int, PlasticLimitSample) -> Unit, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        samples.forEach { sample ->
            val validation = validationResults[sample.id]
            val borderColor = when {
                validation == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                validation.isValid -> Green500
                else -> MaterialTheme.colorScheme.error
            }
            Row(
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.sample_id_short, sample.id), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(end = 8.dp))
                NeuralInput(sample.waterContent, { onValueChange(sample.id, sample.copy(waterContent = it)) }, stringResource(R.string.water_content_percent), Modifier.weight(1f), isError = validation?.errors?.any { it.field == "waterContent" } ?: false)
                if (samples.size > 1) {
                    IconButton(onClick = { onRemove(sample.id) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Add, stringResource(R.string.add_pl_sample), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_pl_sample), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AdvancedAnalysisDashboard(result: CalculationResult) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataPanel(stringResource(R.string.analysis_results)) {
            ResultDisplay(stringResource(R.string.liquid_limit), result.liquidLimit?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A", isPrimary = true)
            ResultDisplay(stringResource(R.string.plastic_limit), result.plasticLimit?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A")
            ResultDisplay(stringResource(R.string.plasticity_index), result.plasticityIndex?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A", isPrimary = true)
            Spacer(modifier = Modifier.height(8.dp))
            ResultDisplay(stringResource(R.string.soil_classification), result.soilClassification, isPrimary = true)
            result.correlationCoefficient?.let { ResultDisplay(stringResource(R.string.correlation_r2), String.format(Locale.US, "%.3f", it * it)) }
        }
        DataPanel(stringResource(R.string.flow_curve_analysis)) {
            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        val onSurfaceVariantColor = colorScheme.onSurfaceVariant
                        description.text = ""; legend.textColor = onSurfaceVariantColor.toArgb(); axisRight.isEnabled = false
                        setDrawGridBackground(false); setNoDataText("Awaiting at least two valid data points..."); setNoDataTextColor(onSurfaceVariantColor.toArgb())
                        setBackgroundColor(ComposeColor.Transparent.toArgb())
                        xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; textColor = onSurfaceVariantColor.toArgb(); gridColor = onSurfaceVariantColor.copy(alpha = 0.2f).toArgb(); axisLineColor = onSurfaceVariantColor.toArgb() }
                        axisLeft.apply { textColor = onSurfaceVariantColor.toArgb(); gridColor = onSurfaceVariantColor.copy(alpha = 0.2f).toArgb(); axisLineColor = onSurfaceVariantColor.toArgb() }
                    }
                },
                update = { chart ->
                    val primaryColor = colorScheme.primary
                    val backgroundColor = colorScheme.background
                    chart.xAxis.apply { removeAllLimitLines(); addLimitLine(LimitLine(25f, "25 Blows").apply { lineColor = Yellow500.toArgb(); lineWidth = 1.5f; enableDashedLine(10f, 10f, 0f); textColor = Yellow500.toArgb(); labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP }) }
                    chart.axisLeft.apply { removeAllLimitLines(); result.liquidLimit?.let { addLimitLine(LimitLine(it, "LL = ${String.format(Locale.US, "%.2f", it)}%").apply { lineColor = primaryColor.toArgb(); lineWidth = 1.5f; enableDashedLine(10f, 10f, 0f); textColor = primaryColor.toArgb(); labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP }) } }
                    if (result.points.isEmpty()) { chart.clear(); chart.invalidate(); return@AndroidView }

                    val pointEntries = result.points.map { Entry(it.blows, it.waterContent) }.sortedBy { it.x }
                    val pointDataSet = LineDataSet(pointEntries, "Data Points").apply {
                        color = primaryColor.toArgb()
                        setCircleColor(primaryColor.toArgb())
                        circleRadius = 5f
                        circleHoleColor = backgroundColor.toArgb()
                        circleHoleRadius = 2.5f
                        lineWidth = 0f
                        setDrawValues(false) // Disable drawing values on points
                    }
                    val bestFitDataSet = LineDataSet(result.bestFitLine, "Flow Line").apply {
                        color = primaryColor.toArgb()
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    chart.data = LineData(pointDataSet, bestFitDataSet)
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
        DataPanel(stringResource(R.string.plasticity_chart_analysis)) {
            PlasticityChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                liquidLimit = result.liquidLimit,
                plasticityIndex = result.plasticityIndex
            )
        }
        result.advancedAnalysis?.let { analysis ->
            DataPanel(stringResource(R.string.advanced_geo_insights)) {
                val behaviorContent = "Swell: ${analysis.behaviorAnalysis.swellPotential}, Compressibility: ${analysis.behaviorAnalysis.compressibility}"
                InsightCard(Icons.Default.Science, stringResource(R.string.behavior_analysis), behaviorContent)
                InsightCard(Icons.Default.Build, stringResource(R.string.engineering_properties), "Est. Friction Angle: ${analysis.engineeringProperties.estimatedFrictionAngle}°")
                InsightCard(Icons.Default.Checklist, stringResource(R.string.recommendations), stringResource(analysis.constructionRecommendations.firstOrNull()?.recommendationResId ?: R.string.rec_general_desc))

                val qualityRec = analysis.qualityAssessment.recommendationsForImprovement.firstOrNull()?.let { stringResource(it) } ?: ""
                val qualityContent = "Data Quality: ${analysis.qualityAssessment.dataQuality}, Reliability: ${analysis.qualityAssessment.calculationReliability}. Rec: $qualityRec"
                InsightCard(Icons.Default.Verified, stringResource(R.string.quality_assessment), qualityContent)
            }
        }
    }
}

@Composable
fun PlasticityChart(modifier: Modifier = Modifier, liquidLimit: Float?, plasticityIndex: Float?) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        val yAxisPadding = 45.dp.toPx(); val xAxisPadding = 35.dp.toPx()
        val chartWidth = size.width - yAxisPadding - 10.dp.toPx(); val chartHeight = size.height - xAxisPadding - 10.dp.toPx()
        val origin = Offset(yAxisPadding, size.height - xAxisPadding)
        val maxX = 100f; val maxY = 60f
        val toChartX = { value: Float -> origin.x + (value / maxX) * chartWidth }
        val toChartY = { value: Float -> origin.y - (value / maxY) * chartHeight }
        val textPaint = android.graphics.Paint().apply { color = onSurfaceVariant.toArgb(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }
        val textPaintRight = android.graphics.Paint(textPaint).apply { textAlign = android.graphics.Paint.Align.RIGHT }

        for (i in 0..10) { val x = toChartX(i * 10f); drawLine(color = onSurfaceVariant.copy(alpha = 0.1f), start = Offset(x, origin.y), end = Offset(x, origin.y - chartHeight)); drawIntoCanvas { it.nativeCanvas.drawText((i * 10).toString(), x, origin.y + 25, textPaint) } }
        drawIntoCanvas { it.nativeCanvas.drawText("Liquid Limit (LL), %", origin.x + chartWidth / 2, size.height, textPaint) }
        for (i in 0..6) { val y = toChartY(i * 10f); drawLine(color = onSurfaceVariant.copy(alpha = 0.1f), start = Offset(origin.x, y), end = Offset(origin.x + chartWidth, y)); drawIntoCanvas { it.nativeCanvas.drawText((i * 10).toString(), origin.x - 12, y + 5, textPaintRight) } }
        drawIntoCanvas { it.nativeCanvas.save(); it.nativeCanvas.rotate(-90f, 15f, size.height / 2); it.nativeCanvas.drawText("Plasticity Index (PI), %", 15f, size.height / 2, textPaint); it.nativeCanvas.restore() }

        val aLinePath = androidx.compose.ui.graphics.Path().apply { moveTo(toChartX(20f), toChartY(0.73f * (20 - 20))); lineTo(toChartX(100f), toChartY(0.73f * (100 - 20))) }
        drawPath(aLinePath, color = onSurfaceVariant.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, pathEffect = ComposePathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
        drawLine(color = primary.copy(alpha = 0.4f), start = Offset(toChartX(50f), origin.y), end = Offset(toChartX(50f), origin.y - chartHeight), strokeWidth = 2f)

        if (liquidLimit != null && plasticityIndex != null) {
            val pointX = toChartX(liquidLimit.coerceIn(0f, 100f)); val pointY = toChartY(plasticityIndex.coerceIn(0f, 60f))
            drawCircle(color = primary, radius = 6.dp.toPx(), center = Offset(pointX, pointY)); drawCircle(color = background, radius = 4.dp.toPx(), center = Offset(pointX, pointY))
        }
    }
}