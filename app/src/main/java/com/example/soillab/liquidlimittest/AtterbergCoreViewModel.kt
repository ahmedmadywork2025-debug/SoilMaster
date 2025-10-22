package com.example.soillab.liquidlimittest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// --- كائن حالة الواجهة (UI State) ---
data class AtterbergUiState(
    val testInfo: TestInfo = TestInfo(),
    val llSamples: List<LiquidLimitSample> = listOf(LiquidLimitSample(id = 1)),
    val plSamples: List<PlasticLimitSample> = listOf(PlasticLimitSample(id = 1)),
    val calculationResult: CalculationResult? = null,
    val llValidation: Map<Int, ValidationResult> = emptyMap(),
    val plValidation: Map<Int, ValidationResult> = emptyMap(),
    val isLoading: Boolean = false,
    val currentReportId: String? = null
)

// --- ViewModel مع حقن التبعيات ---
class AtterbergCoreViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AtterbergUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    private val aiValidator = AISoilDataValidator()
    private val advancedClassifier = AdvancedSoilClassifier()

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            clear()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val report = repository.getAtterbergReportById(reportId)
            if (report != null) {
                _uiState.value = AtterbergUiState(
                    testInfo = report.testInfo,
                    llSamples = report.llSamples,
                    plSamples = report.plSamples,
                    currentReportId = report.id,
                    calculationResult = report.calculationResult
                )
                performAdvancedCalculations()
            } else {
                _userMessage.value = "Error: Could not load report."
                clear()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveReport(context: Context) {
        val currentState = _uiState.value
        if (currentState.calculationResult == null || currentState.testInfo.boreholeNo.isBlank() || currentState.testInfo.sampleNo.isBlank()) {
            _userMessage.value = context.getString(R.string.error_fill_info_and_compute)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val reportId = withContext(Dispatchers.IO) {
                    val report = AtterbergReport(
                        id = currentState.currentReportId ?: UUID.randomUUID().toString(),
                        testInfo = currentState.testInfo,
                        llSamples = currentState.llSamples,
                        plSamples = currentState.plSamples,
                        calculationResult = currentState.calculationResult
                    )
                    repository.saveAtterbergReport(report)
                    report.id
                }
                _uiState.update { it.copy(currentReportId = reportId) }
                _userMessage.value = context.getString(R.string.success_report_saved)
            } catch (e: Exception) {
                _userMessage.value = "❌ Error saving report: ${e.message}"
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun onTestInfoChange(newInfo: TestInfo) { _uiState.update { it.copy(testInfo = newInfo) } }
    fun onLLSampleValueChange(id: Int, updatedSample: LiquidLimitSample) {
        val blowsValid = updatedSample.blows.isEmpty() || updatedSample.blows.toFloatOrNull() != null
        val wcValid = updatedSample.waterContent.isEmpty() || updatedSample.waterContent.toFloatOrNull() != null
        if (blowsValid && wcValid) {
            _uiState.update { state -> state.copy(llSamples = state.llSamples.map { if (it.id == id) updatedSample else it }) }
            performAdvancedCalculations()
        } else {
            // Update the UI with the invalid input to show the user what they typed, but don't trigger recalculation.
            _uiState.update { state -> state.copy(llSamples = state.llSamples.map { if (it.id == id) updatedSample else it }) }
        }
    }
    fun addLLSample() { _uiState.update { state -> state.copy(llSamples = state.llSamples + LiquidLimitSample(id = (state.llSamples.maxOfOrNull { it.id } ?: 0) + 1)) } }
    fun removeLLSample(id: Int) {
        if (_uiState.value.llSamples.size > 1) {
            _uiState.update { state -> state.copy(llSamples = state.llSamples.filterNot { it.id == id }) }
            performAdvancedCalculations()
        }
    }
    fun onPLSampleValueChange(id: Int, updatedSample: PlasticLimitSample) {
        val wcValid = updatedSample.waterContent.isEmpty() || updatedSample.waterContent.toFloatOrNull() != null
        if (wcValid) {
            _uiState.update { state -> state.copy(plSamples = state.plSamples.map { if (it.id == id) updatedSample else it }) }
            performAdvancedCalculations()
        } else {
            _uiState.update { state -> state.copy(plSamples = state.plSamples.map { if (it.id == id) updatedSample else it }) }
        }
    }
    fun addPLSample() { _uiState.update { state -> state.copy(plSamples = state.plSamples + PlasticLimitSample(id = (state.plSamples.maxOfOrNull { it.id } ?: 0) + 1)) } }
    fun removePLSample(id: Int) {
        if (_uiState.value.plSamples.size > 1) {
            _uiState.update { state -> state.copy(plSamples = state.plSamples.filterNot { it.id == id }) }
            performAdvancedCalculations()
        }
    }
    private fun clear() { _uiState.value = AtterbergUiState() }

    fun performAdvancedCalculations() {
        val currentState = _uiState.value
        val llValidation: Map<Int, ValidationResult> = currentState.llSamples.associate { it.id to aiValidator.validateLiquidLimitSample(it) }
        val plValidation: Map<Int, ValidationResult> = currentState.plSamples.associate { it.id to aiValidator.validatePlasticLimitSample(it) }

        val validLLPoints: List<DataPoint> = currentState.llSamples.mapNotNull { sample ->
            if (llValidation[sample.id]?.isValid == true) {
                val blows = sample.blows.toFloatOrNull()
                val wc = sample.waterContent.toFloatOrNull()
                if (blows != null && wc != null) DataPoint(blows = blows, waterContent = wc) else null
            } else { null }
        }

        if (validLLPoints.size < 2) {
            _uiState.update { it.copy(calculationResult = null, llValidation = llValidation, plValidation = plValidation) }
            return
        }

        val (slope, intercept, correlation) = calculateBestFitLineWithCorrelation(validLLPoints)
        if (slope.isNaN() || intercept.isNaN()) {
            _uiState.update { it.copy(calculationResult = null, llValidation = llValidation, plValidation = plValidation) }
            return
        }

        val liquidLimit = (intercept + slope * log10(25.0)).toFloat()
        val plasticLimit = currentState.plSamples
            .mapNotNull { if (plValidation[it.id]?.isValid == true) it.waterContent.toFloatOrNull() else null }
            .average().toFloat().takeIf { !it.isNaN() }
        val plasticityIndex = if (plasticLimit != null && liquidLimit > plasticLimit) liquidLimit - plasticLimit else null
        val isNonPlastic = plasticLimit == null || (plasticityIndex != null && plasticityIndex <= 0)
        val advancedAnalysis = advancedClassifier.classifySoilAdvanced(liquidLimit, if (isNonPlastic) 0f else plasticityIndex, validLLPoints.map { it.blows to it.waterContent }, correlation)

        val newResult = CalculationResult(
            liquidLimit = liquidLimit,
            plasticLimit = plasticLimit,
            plasticityIndex = plasticityIndex,
            points = validLLPoints,
            bestFitLine = calculateBestFitLinePoints(validLLPoints, slope, intercept),
            isNonPlastic = isNonPlastic,
            soilClassification = advancedAnalysis.basicClassification.symbol,
            advancedAnalysis = advancedAnalysis,
            correlationCoefficient = correlation
        )
        _uiState.update { it.copy(calculationResult = newResult, llValidation = llValidation, plValidation = plValidation) }
    }

    fun loadExampleData(context: Context) {
        val example = AtterbergExampleDataGenerator.generate()
        val description = context.getString(example.descriptionResId)
        _uiState.update {
            it.copy(
                llSamples = example.llSamples,
                plSamples = example.plSamples,
                testInfo = it.testInfo.copy(sampleDescription = description),
                calculationResult = null,
                llValidation = emptyMap(),
                plValidation = emptyMap()
            )
        }
        viewModelScope.launch { _userMessage.value = context.getString(R.string.example_data_loaded) }
    }

    private fun calculateBestFitLineWithCorrelation(points: List<DataPoint>): Triple<Double, Double, Float> {
        val n = points.size
        val x = points.map { log10(it.blows.toDouble()) }
        val y = points.map { it.waterContent.toDouble() }
        val sumX = x.sum(); val sumY = y.sum(); val sumXY = x.zip(y).sumOf { it.first * it.second }; val sumX2 = x.sumOf { it.pow(2) }; val sumY2 = y.sumOf{it.pow(2)}
        val denominator = n * sumX2 - sumX.pow(2)
        if (denominator == 0.0) return Triple(Double.NaN, Double.NaN, 0f)
        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n
        val rNumerator = n * sumXY - sumX * sumY
        val rDenom = sqrt((n * sumX2 - sumX.pow(2)) * (n * sumY2 - sumY.pow(2)))
        val r = if (rDenom != 0.0) rNumerator / rDenom else 0.0
        return Triple(slope, intercept, r.toFloat())
    }

    private fun calculateBestFitLinePoints(points: List<DataPoint>, slope: Double, intercept: Double): List<Entry> {
        if (points.isEmpty()) return emptyList()
        val minBlows = points.minOfOrNull { it.blows } ?: 10f
        val maxBlows = points.maxOfOrNull { it.blows } ?: 40f
        return (minBlows.toInt()..maxBlows.toInt()).map { blows -> Entry(blows.toFloat(), (slope * log10(blows.toFloat()) + intercept).toFloat()) }
    }
}
