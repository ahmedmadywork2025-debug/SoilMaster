package com.example.soillab.cbr

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

// --- Main CBR Screen ---
@Composable
fun CBRTestScreenImproved(
    viewModel: CBRViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()
    val highlightedValue = uiState.highlightedValue

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Data & Curve", "Results")

    LaunchedEffect(reportIdToLoad) { viewModel.loadReportForEditing(reportIdToLoad) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }
    LaunchedEffect(highlightedValue) {
        highlightedValue?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTabIndex) {
                    0 -> SetupTab(viewModel, uiState)
                    1 -> DataAndCurveTab(viewModel, uiState)
                    2 -> ResultsTab(viewModel, uiState)
                }
            }
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SetupTab(viewModel: CBRViewModel, uiState: CBRUiState) {
    TestInfoSection(uiState.parameters.testInfo) { newInfo ->
        viewModel.onParamsChange(uiState.parameters.copy(testInfo = newInfo))
    }
    InputAndParametersSection(viewModel, uiState)
}

@Composable
fun DataAndCurveTab(viewModel: CBRViewModel, uiState: CBRUiState) {
    val context = LocalContext.current
    DataPointsList(uiState.points, viewModel::removePoint)
    CbrChart(
        points = uiState.points,
        correctedPoints = uiState.correctedPoints,
        onValueSelected = viewModel::onChartValueSelected,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
    Spacer(Modifier.height(16.dp))
    ActionButtons(onCompute = viewModel::computeCBR, onLoadExample = { viewModel.loadExampleData(context) })
}

@Composable
fun ResultsTab(viewModel: CBRViewModel, uiState: CBRUiState) {
    AnimatedVisibility(visible = uiState.result != null) {
        val result = uiState.result
        if (result != null) {
            Column {
                ResultSection(result, uiState.requiredCbr, viewModel::onRequiredCbrChange)
                if (result.insights != null) {
                    EngineeringPropertiesPanel(result.insights!!, result)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputAndParametersSection(viewModel: CBRViewModel, uiState: CBRUiState) {
    var purposeMenuExpanded by remember { mutableStateOf(false) }

    DataPanel(stringResource(R.string.cbr_input_parameters)) {
        // Test Purpose Dropdown
        ExposedDropdownMenuBox(
            expanded = purposeMenuExpanded,
            onExpandedChange = { purposeMenuExpanded = !purposeMenuExpanded },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = stringResource(id = uiState.parameters.testPurpose.displayNameResId),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.test_purpose_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = purposeMenuExpanded,
                onDismissRequest = { purposeMenuExpanded = false }
            ) {
                TestPurpose.values().forEach { purpose ->
                    DropdownMenuItem(
                        text = { Text(stringResource(purpose.displayNameResId)) },
                        onClick = {
                            viewModel.onTestPurposeChange(purpose)
                            purposeMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuralInput(uiState.penetrationInput, viewModel::onPenetrationChange, stringResource(R.string.penetration_mm), Modifier.weight(1f))
            NeuralInput(uiState.dialReadingInput, viewModel::onDialReadingChange, stringResource(R.string.dial_reading), Modifier.weight(1f))
            Button(onClick = viewModel::addPoint, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.add_point))
            }
        }
        NeuralInput(
            value = uiState.parameters.provingRingFactor,
            onValueChange = { viewModel.onParamsChange(uiState.parameters.copy(provingRingFactor = it)) },
            label = stringResource(R.string.proving_ring_factor),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralInput(uiState.parameters.moistureContent, { viewModel.onParamsChange(uiState.parameters.copy(moistureContent = it)) }, stringResource(R.string.moisture_percent), Modifier.weight(1f))
            NeuralInput(uiState.parameters.dryDensity, { viewModel.onParamsChange(uiState.parameters.copy(dryDensity = it)) }, stringResource(R.string.dry_density), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralInput(uiState.parameters.surchargeWeight, { viewModel.onParamsChange(uiState.parameters.copy(surchargeWeight = it)) }, stringResource(R.string.surcharge_kg), Modifier.weight(1f))
            NeuralInput(uiState.parameters.soakingTime, { viewModel.onParamsChange(uiState.parameters.copy(soakingTime = it)) }, stringResource(R.string.soaking_days), Modifier.weight(1f))
        }
    }
}

@Composable
fun DataPointsList(points: List<CBRDataPoint>, onRemove: (CBRDataPoint) -> Unit) {
    AnimatedVisibility(visible = points.isNotEmpty()) {
        DataPanel(stringResource(R.string.data_points)) {
            Column {
                points.forEach { point ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.data_point_display, point.penetration, point.load),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        IconButton(onClick = { onRemove(point) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ResultSection(result: CBRCalculationResult, requiredCbr: String, onRequiredCbrChange: (String) -> Unit) {
    val requiredCbrValue = requiredCbr.toDoubleOrNull()
    val isPass = requiredCbrValue != null && result.finalCbrValue >= requiredCbrValue

    DataPanel(stringResource(R.string.cbr_analysis_results)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeuralInput(
                value = requiredCbr,
                onValueChange = onRequiredCbrChange,
                label = stringResource(R.string.required_cbr),
                modifier = Modifier.weight(1f)
            )
            if (requiredCbrValue != null) {
                val (text, color) = if (isPass) {
                    Pair(stringResource(R.string.pass), Green500)
                } else {
                    Pair(stringResource(R.string.fail), MaterialTheme.colorScheme.error)
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        ResultDisplayWithInfo(
            title = stringResource(R.string.final_cbr_value),
            value = "${String.format(Locale.US, "%.1f", result.finalCbrValue)} %%",
            infoTitleResId = R.string.info_final_cbr_title,
            infoContentResId = R.string.info_final_cbr_content,
            isPrimary = true,
            valueColor = MaterialTheme.colorScheme.primary
        )
        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(result.messageResId),
            color = if (result.messageResId == R.string.cbr_calc_warning_5mm) Yellow500 else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        ResultDisplay(stringResource(R.string.cbr_at_2_5), "${String.format(Locale.US, "%.1f", result.cbrAt2_5)} %%")
        ResultDisplay(stringResource(R.string.cbr_at_5_0), "${String.format(Locale.US, "%.1f", result.cbrAt5_0)} %%")
        ResultDisplay(stringResource(R.string.load_at_2_5), "${String.format(Locale.US, "%.3f", result.loadAt2_5)} kN")
        ResultDisplay(stringResource(R.string.load_at_5_0), "${String.format(Locale.US, "%.3f", result.loadAt5_0)} kN")
        ResultDisplayWithInfo(
            title = stringResource(R.string.curve_corrected),
            value = if (result.isCorrected) stringResource(R.string.yes) else stringResource(R.string.no),
            infoTitleResId = R.string.info_corrected_title,
            infoContentResId = R.string.info_corrected_content,
            valueColor = if(result.isCorrected) Yellow500 else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun EngineeringPropertiesPanel(insights: CBRInsights, result: CBRCalculationResult) {
    DataPanel(stringResource(R.string.cbr_engineering_properties)) {
        val ratingColor = try { Color(android.graphics.Color.parseColor(insights.ratingColorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.soil_quality_rating), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(insights.qualityRatingResId),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                modifier = Modifier
                    .background(ratingColor, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

        ResultDisplayWithInfo(
            title = stringResource(R.string.est_resilient_modulus),
            value = insights.estimatedResilientModulus,
            infoTitleResId = R.string.info_mr_title,
            infoContentResId = R.string.info_mr_content
        )
        if (insights.estimatedShearStrength != "0 - 0 kPa") {
            ResultDisplayWithInfo(
                title = stringResource(R.string.est_shear_strength),
                value = insights.estimatedShearStrength,
                infoTitleResId = R.string.info_su_title,
                infoContentResId = R.string.info_su_content
            )
        }
        result.predictedKValue?.let {
            ResultDisplayWithInfo(
                title = stringResource(R.string.est_subgrade_modulus),
                value = "${String.format(Locale.US, "%.0f", it)} MN/m³",
                infoTitleResId = R.string.info_k_title,
                infoContentResId = R.string.info_k_content
            )
        }
        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

        InsightCard(icon = Icons.Default.Analytics, title = stringResource(R.string.curve_interpretation), content = stringResource(insights.curveInterpretationResId))
        InsightCard(icon = Icons.Default.Checklist, title = stringResource(R.string.primary_recommendation), content = stringResource(insights.primaryRecommendationResId))
    }
}


@Composable
fun CbrChart(
    points: List<CBRDataPoint>,
    correctedPoints: List<CBRDataPoint>?,
    onValueSelected: (Entry?) -> Unit,
    modifier: Modifier = Modifier
) {
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.text = ""
                axisRight.isEnabled = false
                legend.textColor = onSurfaceVariantColor.toArgb()
                setBackgroundColor(Color.Transparent.toArgb())
                setNoDataText("Enter data points to see the curve.")
                setNoDataTextColor(onSurfaceVariantColor.toArgb())
                setOnChartValueSelectedListener(object: OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) { onValueSelected(e) }
                    override fun onNothingSelected() { onValueSelected(null) }
                })

                axisLeft.apply {
                    textColor = onSurfaceVariantColor.toArgb()
                    gridColor = onSurfaceVariantColor.copy(alpha = 0.2f).toArgb()
                    axisLineColor = onSurfaceVariantColor.toArgb()
                    axisMinimum = 0f
                }

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = onSurfaceVariantColor.toArgb()
                    gridColor = onSurfaceVariantColor.copy(alpha = 0.2f).toArgb()
                    axisLineColor = onSurfaceVariantColor.toArgb()
                    granularity = 1f
                    axisMinimum = 0f

                    val ll25 = LimitLine(2.5f, "2.5 mm").apply {
                        lineColor = Yellow500.toArgb(); lineWidth = 1.5f; enableDashedLine(10f, 10f, 0f)
                        textColor = Yellow500.toArgb(); labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    }
                    val ll50 = LimitLine(5.0f, "5.0 mm").apply {
                        lineColor = Yellow500.toArgb(); lineWidth = 1.5f; enableDashedLine(10f, 10f, 0f)
                        textColor = Yellow500.toArgb(); labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    }
                    addLimitLine(ll25); addLimitLine(ll50)
                }
            }
        },
        update = { chart ->
            val originalEntries = points.map { Entry(it.penetration.toFloat(), it.load.toFloat()) }
            val originalDataSet = LineDataSet(originalEntries, "Original Curve").apply {
                lineWidth = 2f; circleRadius = 4f; setDrawValues(false)
                color = primaryColor.toArgb(); setCircleColor(primaryColor.toArgb())
            }
            val dataSets = mutableListOf<ILineDataSet>(originalDataSet)

            correctedPoints?.let {
                val correctedEntries = it.map { p -> Entry(p.penetration.toFloat(), p.load.toFloat()) }
                val correctedDataSet = LineDataSet(correctedEntries, "Corrected Curve").apply {
                    lineWidth = 2.0f; circleRadius = 3f; setDrawValues(false)
                    color = primaryColor.toArgb(); setCircleColor(primaryColor.toArgb())
                    enableDashedLine(10f, 5f, 0f)
                }
                dataSets.add(correctedDataSet)
            }

            chart.data = LineData(dataSets)
            chart.invalidate()
        },
        modifier = modifier
    )
}