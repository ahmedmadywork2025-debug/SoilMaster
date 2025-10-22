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
