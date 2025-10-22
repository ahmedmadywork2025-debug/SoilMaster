package com.example.soillab.proctor

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Yellow500
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// --- UI State & ViewModel ---
data class ProctorUiState(
    val testInfo: TestInfo = TestInfo(),
    val parameters: ProctorTestParameters = ProctorTestParameters(),
    val points: List<ProctorDataPoint> = emptyList(),
    val moistureInput: String = "",
    val wetWeightInput: String = "",
    val result: ProctorResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null,
    val fieldMoistureContent: String = "",
    val requiredCompaction: String = "95"
)

class ProctorViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProctorUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun onTestInfoChange(newInfo: TestInfo) {
        _uiState.update { it.copy(testInfo = newInfo) }
    }

    fun onParamsChange(newParams: ProctorTestParameters) {
        _uiState.update { it.copy(parameters = newParams) }
    }

    fun onMoistureInputChange(input: String) {
        _uiState.update { it.copy(moistureInput = input) }
    }

    fun onWetWeightInputChange(input: String) {
        _uiState.update { it.copy(wetWeightInput = input) }
    }

    fun onFieldMoistureChange(input: String) {
        _uiState.update { it.copy(fieldMoistureContent = input) }
        recalculateAchievableDensity(input)
    }

    fun onRequiredCompactionChange(input: String) {
        _uiState.update { it.copy(requiredCompaction = input) }
        recalculateCompactionBand(input)
    }

    fun addPoint() {
        viewModelScope.launch {
            val state = _uiState.value
            val moisture = state.moistureInput.toDoubleOrNull()
            val wetSoilAndMoldWeight = state.wetWeightInput.toDoubleOrNull()
            val moldWeight = state.parameters.moldWeight.toDoubleOrNull()
            val moldVolume = state.parameters.moldVolume.toDoubleOrNull()

            if (moisture == null || wetSoilAndMoldWeight == null || moldWeight == null || moldVolume == null || moldVolume == 0.0) {
                _userMessage.emit("Please fill all parameters and point data correctly.")
                return@launch
            }

            val wetDensity = (wetSoilAndMoldWeight - moldWeight) / moldVolume
            val dryDensity = wetDensity / (1 + moisture / 100.0)

            val newPoint = ProctorDataPoint(moisture, wetDensity, dryDensity)
            val updatedPoints = (state.points + newPoint).sortedBy { it.moistureContent }

            _uiState.update {
                it.copy(
                    points = updatedPoints,
                    moistureInput = "",
                    wetWeightInput = ""
                )
            }
        }
    }

    fun removePoint(point: ProctorDataPoint) {
        _uiState.update { state ->
            val updatedPoints = state.points.filterNot { it == point }
            state.copy(points = updatedPoints, result = null)
        }
    }

    fun calculateProctorCurve() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.points.size < 3) {
                _userMessage.emit("At least 3 points are needed to calculate the curve.")
                return@launch
            }

            if (state.points.maxOf { it.dryDensity } < state.points.first().dryDensity || state.points.maxOf { it.dryDensity } < state.points.last().dryDensity){
                _userMessage.emit("The peak of the curve has not been defined. Please add points on both sides of the optimum moisture content.")
            }

            val result = ProctorCalculator.calculate(state.points, state.parameters)
            _uiState.update { it.copy(result = result) }
            recalculateCompactionBand(state.requiredCompaction)
        }
    }

    private fun recalculateAchievableDensity(fieldMoistureStr: String) {
        val fieldMoisture = fieldMoistureStr.toDoubleOrNull()
        val currentResult = _uiState.value.result
        if (fieldMoisture != null && currentResult != null) {
            val achievableDensity = ProctorCalculator.getDensityAtMoisture(currentResult.fittedCurvePoints, fieldMoisture)
            _uiState.update { it.copy(result = currentResult.copy(achievableDryDensity = achievableDensity)) }
        }
    }

    private fun recalculateCompactionBand(requiredCompactionStr: String) {
        val requiredCompaction = requiredCompactionStr.toDoubleOrNull()
        val currentResult = _uiState.value.result
        if (requiredCompaction != null && currentResult != null) {
            val band = ProctorCalculator.getMoistureRangeForCompaction(
                currentResult.fittedCurvePoints,
                currentResult.maxDryDensity,
                requiredCompaction
            )
            _uiState.update { it.copy(result = currentResult.copy(compactionBand = band)) }
        }
    }

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.value = ProctorUiState()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getProctorReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        testInfo = report.parameters.testInfo,
                        parameters = report.parameters,
                        result = report.result,
                        points = report.result.points,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveReport(context: Context) {
        val currentState = _uiState.value
        if (currentState.result == null || currentState.testInfo.boreholeNo.isBlank()) {
            viewModelScope.launch { _userMessage.emit(context.getString(R.string.error_fill_info_and_compute)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val reportId = withContext(Dispatchers.IO) {
                    val report = ProctorReport(
                        id = currentState.currentReportId ?: UUID.randomUUID().toString(),
                        parameters = currentState.parameters.copy(testInfo = currentState.testInfo),
                        result = currentState.result!!
                    )
                    repository.saveProctorReport(report)
                    report.id
                }
                _uiState.update { it.copy(currentReportId = reportId) }
                _userMessage.emit(context.getString(R.string.success_report_saved))
            } catch (e: Exception) {
                _userMessage.emit("❌ Error saving report: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch {
            val example = ProctorExampleDataGenerator.generate()
            _uiState.update {
                it.copy(
                    parameters = example.parameters,
                    points = example.points,
                    testInfo = it.testInfo.copy(sampleDescription = context.getString(example.descriptionResId)),
                    result = null,
                    moistureInput = "",
                    wetWeightInput = ""
                )
            }
            _userMessage.emit(context.getString(R.string.example_data_loaded))
        }
    }
}


@Composable
fun ProctorScreenImproved(
    viewModel: ProctorViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Data Entry", "Results")

    LaunchedEffect(reportIdToLoad) {
        viewModel.loadReportForEditing(reportIdToLoad)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (selectedTabIndex) {
                0 -> SetupTab(uiState, viewModel)
                1 -> DataEntryTab(uiState, viewModel)
                2 -> ResultsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun SetupTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
    TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)
    TestSetupPanel(
        parameters = uiState.parameters,
        onParamsChange = viewModel::onParamsChange
    )
}

@Composable
fun DataEntryTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
    val context = LocalContext.current
    DataPointsInputPanel(
        uiState = uiState,
        onMoistureChange = viewModel::onMoistureInputChange,
        onWetWeightChange = viewModel::onWetWeightInputChange,
        onAddPoint = viewModel::addPoint
    )
    DataPointsList(points = uiState.points, onRemove = viewModel::removePoint)
    ActionButtons(
        onCompute = { viewModel.calculateProctorCurve() },
        onLoadExample = { viewModel.loadExampleData(context) }
    )
}

@Composable
fun ResultsTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
    AnimatedVisibility(visible = uiState.result != null) {
        uiState.result?.let { result ->
            ResultDashboard(
                result = result,
                fieldMoistureContent = uiState.fieldMoistureContent,
                onFieldMoistureChange = viewModel::onFieldMoistureChange,
                requiredCompaction = uiState.requiredCompaction,
                onRequiredCompactionChange = viewModel::onRequiredCompactionChange,
                testParameters = uiState.parameters
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSetupPanel(
    parameters: ProctorTestParameters,
    onParamsChange: (ProctorTestParameters) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showGsInfo by remember { mutableStateOf(false) }

    if (showGsInfo) {
        AlertDialog(
            onDismissRequest = { showGsInfo = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.info_gs_title), color = MaterialTheme.colorScheme.primary) },
            text = { Text(stringResource(R.string.info_gs_content), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showGsInfo = false }) {
                    Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    DataPanel(title = stringResource(R.string.proctor_setup_title)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = stringResource(id = parameters.testType.displayNameResId),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.proctor_test_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ProctorTestType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.displayNameResId)) },
                        onClick = {
                            onParamsChange(parameters.copy(testType = type))
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralInput(
                value = parameters.moldWeight,
                onValueChange = { onParamsChange(parameters.copy(moldWeight = it)) },
                label = stringResource(R.string.proctor_mold_weight_g),
                modifier = Modifier.weight(1f)
            )
            NeuralInput(
                value = parameters.moldVolume,
                onValueChange = { onParamsChange(parameters.copy(moldVolume = it)) },
                label = stringResource(R.string.proctor_mold_volume_cm3),
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuralInput(
                value = parameters.specificGravity,
                onValueChange = { onParamsChange(parameters.copy(specificGravity = it)) },
                label = stringResource(R.string.proctor_specific_gravity),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showGsInfo = true }) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = stringResource(R.string.more_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DataPointsInputPanel(
    uiState: ProctorUiState,
    onMoistureChange: (String) -> Unit,
    onWetWeightChange: (String) -> Unit,
    onAddPoint: () -> Unit
) {
    var showMoistureCalculator by remember { mutableStateOf(false) }

    if (showMoistureCalculator) {
        MoistureContentCalculatorDialog(
            onDismiss = { showMoistureCalculator = false },
            onCalculate = { moisture ->
                onMoistureChange(moisture)
                showMoistureCalculator = false
            }
        )
    }

    DataPanel(title = stringResource(R.string.proctor_data_entry_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuralInput(
                value = uiState.wetWeightInput,
                onValueChange = onWetWeightChange,
                label = stringResource(R.string.proctor_wet_soil_mold_g),
                modifier = Modifier.weight(1.5f)
            )
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                NeuralInput(
                    value = uiState.moistureInput,
                    onValueChange = onMoistureChange,
                    label = stringResource(R.string.proctor_moisture_content_percent),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showMoistureCalculator = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Calculate, contentDescription = "Calculate Moisture Content")
                }
            }

            Button(onClick = onAddPoint, modifier = Modifier.padding(top = 20.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun DataPointsList(points: List<ProctorDataPoint>, onRemove: (ProctorDataPoint) -> Unit) {
    AnimatedVisibility(visible = points.isNotEmpty()) {
        DataPanel(stringResource(R.string.data_points)) {
            Column {
                points.forEach { point ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MC: ${point.moistureContent}%, Dry Density: ${String.format("%.3f", point.dryDensity)} g/cm³",
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
fun ResultDashboard(
    result: ProctorResult,
    fieldMoistureContent: String,
    onFieldMoistureChange: (String) -> Unit,
    requiredCompaction: String,
    onRequiredCompactionChange: (String) -> Unit,
    testParameters: ProctorTestParameters
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataPanel(stringResource(R.string.proctor_results_title)) {
            ResultDisplay(stringResource(R.string.proctor_mdd), "${String.format("%.3f", result.maxDryDensity)} g/cm³", isPrimary = true)
            ResultDisplay(stringResource(R.string.proctor_omc), "${String.format("%.1f", result.optimumMoistureContent)}%", isPrimary = true)
        }

        ProctorChart(result = result, requiredCompaction = requiredCompaction)

        DataPanel(title = stringResource(R.string.proctor_field_sim_title)) {
            NeuralInput(
                value = fieldMoistureContent,
                onValueChange = onFieldMoistureChange,
                label = stringResource(R.string.proctor_field_mc_percent)
            )
            result.achievableDryDensity?.let {
                ResultDisplay(stringResource(R.string.proctor_achievable_density), "${String.format("%.3f", it)} g/cm³")
            }
            Spacer(modifier = Modifier.height(8.dp))
            NeuralInput(
                value = requiredCompaction,
                onValueChange = onRequiredCompactionChange,
                label = stringResource(R.string.proctor_required_compaction)
            )
            result.compactionBand?.let {
                val (min, max) = it
                ResultDisplay(
                    title = stringResource(R.string.proctor_acceptable_mc_range),
                    value = "${String.format("%.1f", min)}% - ${String.format("%.1f", max)}%"
                )
            }
        }

        ProctorPredictionPanel(result, testParameters)
    }
}

@Composable
fun ProctorPredictionPanel(result: ProctorResult, params: ProctorTestParameters) {
    // Implement logic to get predictions
    // val predictions = ProctorCalculator.predictProperties(result, params)

    // For now, static placeholders
    val predictedCbr = 15f
    val swellPotential = "Low"

    DataPanel(title = stringResource(id = R.string.prediction_panel_title)) {
        ResultDisplayWithInfo(
            title = stringResource(R.string.proctor_predicted_cbr),
            value = "~ ${String.format("%.0f", predictedCbr)}%",
            infoTitleResId = R.string.info_prediction_title,
            infoContentResId = R.string.info_prediction_content
        )
        ResultDisplayWithInfo(
            title = stringResource(R.string.proctor_swell_potential),
            value = swellPotential,
            infoTitleResId = R.string.info_swell_title,
            infoContentResId = R.string.info_swell_content
        )
    }
}


@Composable
fun ProctorChart(result: ProctorResult, requiredCompaction: String) {
    val colorScheme = MaterialTheme.colorScheme
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 16.dp),
        factory = { context -> LineChart(context).apply { setupProctorChart(this, colorScheme) } },
        update = { chart ->
            val points = result.points.map { Entry(it.moistureContent.toFloat(), it.dryDensity.toFloat()) }
            val curve = result.fittedCurvePoints.map { Entry(it.first, it.second) }
            val zav = result.zavCurvePoints.map { Entry(it.first, it.second) }

            val pointsDS = LineDataSet(points, "Data Points").apply {
                color = colorScheme.primary.toArgb()
                setCircleColor(colorScheme.primary.toArgb())
                circleRadius = 5f
                setDrawValues(false)
                lineWidth = 0f
            }

            val curveDS = LineDataSet(curve, "Compaction Curve").apply {
                color = colorScheme.primary.toArgb()
                lineWidth = 2.5f
                setDrawCircles(false)
                setDrawValues(false)
            }

            val zavDS = LineDataSet(zav, "ZAV Curve").apply {
                color = Yellow500.toArgb()
                lineWidth = 1.5f
                enableDashedLine(10f, 5f, 0f)
                setDrawCircles(false)
                setDrawValues(false)
            }

            chart.axisLeft.removeAllLimitLines()

            val requiredCompactionValue = requiredCompaction.toFloatOrNull() ?: 95f
            val requiredDensity = result.maxDryDensity * (requiredCompactionValue / 100f)
            val compactionLine = LimitLine(requiredDensity, "$requiredCompaction% MDD").apply {
                lineColor = colorScheme.secondary.toArgb()
                lineWidth = 1f
                enableDashedLine(20f, 10f, 0f)
                textColor = colorScheme.onSurfaceVariant.toArgb()
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
            chart.axisLeft.addLimitLine(compactionLine)

            // Add vertical lines for OMC
            val omcLine = LimitLine(result.optimumMoistureContent, "OMC").apply {
                lineColor = colorScheme.primary.toArgb()
                lineWidth = 1.5f
                enableDashedLine(10f, 10f, 0f)
                textColor = colorScheme.primary.toArgb()
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
            chart.xAxis.addLimitLine(omcLine)


            chart.data = LineData(listOf(pointsDS, curveDS, zavDS))
            chart.invalidate()
        }
    )
}

fun setupProctorChart(chart: LineChart, colorScheme: ColorScheme) {
    chart.description.isEnabled = false
    chart.axisRight.isEnabled = false
    chart.legend.textColor = colorScheme.onSurfaceVariant.toArgb()
    chart.setBackgroundColor(Color.Transparent.toArgb())
    chart.setNoDataText("Enter data and compute to see the curve.")
    chart.setNoDataTextColor(colorScheme.onSurfaceVariant.toArgb())

    chart.axisLeft.apply {
        textColor = colorScheme.onSurfaceVariant.toArgb()
        gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
        axisLineColor = colorScheme.onSurfaceVariant.toArgb()
    }

    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        textColor = colorScheme.onSurfaceVariant.toArgb()
        gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
        axisLineColor = colorScheme.onSurfaceVariant.toArgb()
        granularity = 1f
    }
}
