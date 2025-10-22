package com.example.soillab.cbr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

// --- UI State ---
data class CBRUiState(
    val parameters: CBRTestParameters = CBRTestParameters(),
    val penetrationInput: String = "",
    val dialReadingInput: String = "",
    val points: List<CBRDataPoint> = emptyList(),
    val correctedPoints: List<CBRDataPoint>? = null,
    val result: CBRCalculationResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null,
    val requiredCbr: String = "",
    val highlightedValue: String? = null
)

// --- ViewModel ---
class CBRViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CBRUiState())
    val uiState = _uiState.asStateFlow()
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.value = CBRUiState()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getCBRReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        parameters = report.testParameters,
                        points = report.points,
                        correctedPoints = report.correctedPoints,
                        result = report.result,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveReport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state.result == null || state.parameters.testInfo.boreholeNo.isBlank()) {
                _userMessage.value = context.getString(R.string.error_fill_info_and_compute)
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val report = CBRReport(
                id = state.currentReportId ?: UUID.randomUUID().toString(),
                testParameters = state.parameters,
                points = state.points,
                correctedPoints = state.correctedPoints,
                result = state.result
            )
            repository.saveCBRReport(report)
            _uiState.update { it.copy(isLoading = false, currentReportId = report.id) }
            _userMessage.value = context.getString(R.string.success_report_saved)
        }
    }

    fun onParamsChange(newParams: CBRTestParameters) = _uiState.update { it.copy(parameters = newParams) }
    fun onPenetrationChange(input: String) = _uiState.update { it.copy(penetrationInput = input) }
    fun onDialReadingChange(input: String) = _uiState.update { it.copy(dialReadingInput = input) }
    fun onRequiredCbrChange(input: String) = _uiState.update { it.copy(requiredCbr = input) }

    fun onTestPurposeChange(purpose: TestPurpose) {
        val standardSurcharge = when(purpose) {
            TestPurpose.SUBGRADE -> "2.27" // 5 lbs
            TestPurpose.SUBBASE -> "4.54" // 10 lbs
            TestPurpose.BASE_COURSE -> "4.54" // 10 lbs
        }
        _uiState.update { it.copy(parameters = it.parameters.copy(testPurpose = purpose, surchargeWeight = standardSurcharge)) }
    }


    fun addPoint() {
        val pen = _uiState.value.penetrationInput.toDoubleOrNull()
        val dialReading = _uiState.value.dialReadingInput.toDoubleOrNull()
        val factor = _uiState.value.parameters.provingRingFactor.toDoubleOrNull()

        if (pen == null || dialReading == null || factor == null || factor == 0.0) {
            _userMessage.value = "Please enter valid numbers for penetration, dial reading, and a non-zero factor."
            return
        }
        val load = dialReading * factor
        val newPoint = CBRDataPoint(penetration = pen, load = load)
        _uiState.update { state ->
            val updatedPoints = (state.points + newPoint).sortedBy { it.penetration }
            state.copy(points = updatedPoints, penetrationInput = "", dialReadingInput = "", result = null, correctedPoints = null)
        }
    }

    fun removePoint(point: CBRDataPoint) {
        _uiState.update { state ->
            state.copy(points = state.points - point, result = null, correctedPoints = null)
        }
    }

    fun computeCBR() {
        val state = _uiState.value
        if (state.points.size < 2) {
            _userMessage.value = "At least two data points are required."
            return
        }

        val (calculationResult, corrected) = CBRCalculator.calculate(state.points, state.parameters)

        if (calculationResult == null) {
            _userMessage.value = "Cannot compute. Penetration range is insufficient."
            _uiState.update { it.copy(correctedPoints = corrected) }
            return
        }
        _uiState.update { it.copy(result = calculationResult, correctedPoints = corrected) }
    }

    fun onChartValueSelected(entry: Entry?) {
        if (entry == null) {
            _uiState.update { it.copy(highlightedValue = null) }
            return
        }
        val message = String.format(Locale.US, "Pen: %.2f mm, Load: %.3f kN", entry.x, entry.y)
        _uiState.update { it.copy(highlightedValue = message) }
    }

    fun loadExampleData(context: Context) {
        val example = CBRExampleDataGenerator.generate()
        val description = context.getString(example.descriptionResId)
        _uiState.update {
            it.copy(
                points = example.points,
                result = null,
                correctedPoints = null,
                parameters = it.parameters.copy(testInfo = it.parameters.testInfo.copy(sampleDescription = description))
            )
        }
        _userMessage.value = context.getString(R.string.example_data_loaded)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
