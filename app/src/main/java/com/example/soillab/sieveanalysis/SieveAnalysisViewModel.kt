package com.example.soillab.sieveanalysis

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

// --- UI State ---
data class SieveUiState(
    val testInfo: TestInfo = TestInfo(),
    val parameters: ClassificationParameters = ClassificationParameters(),
    val sieves: List<Sieve> = Sieve.standardSet(),
    val result: SieveAnalysisResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null,
    val allSpecifications: List<Specification> = emptyList(),
    val selectedSpecification: Specification? = null,
    val showSpecComparison: Boolean = false,
    val highlightedValue: String? = null,
    val selectedSampleType: SampleType = SampleType.SOIL,
    val isCustomSpecEditing: Boolean = false,
    val customSpecSieves: List<Sieve> = Sieve.standardSet(),
    val customSpecLimits: Map<Double, Pair<String, String>> = emptyMap(), // opening -> (min, max)
    val customSpecName: String = ""
)

// --- ViewModel ---
class SieveAnalysisViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SieveUiState())
    val uiState = _uiState.asStateFlow()
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.customSpecifications.collect { customSpecs ->
                val predefinedSpecs = SpecificationRepository.getPredefinedSpecs()
                _uiState.update {
                    it.copy(allSpecifications = predefinedSpecs + customSpecs.sortedBy { s -> s.name })
                }
            }
        }
    }

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.value = SieveUiState()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSieveAnalysisReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        testInfo = report.testInfo,
                        parameters = report.parameters,
                        sieves = report.result?.sieves ?: report.sieves,
                        result = report.result,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onSieveWeightChange(opening: Double, retainedWeight: String) {
        val isValidInput = retainedWeight.isEmpty() || retainedWeight.toDoubleOrNull() != null
        _uiState.update { state ->
            val updatedSieves = state.sieves.map {
                if (it.opening == opening) it.copy(retainedWeight = if (isValidInput) retainedWeight else it.retainedWeight) else it
            }
            state.copy(sieves = updatedSieves)
        }
        if (isValidInput) {
            performCalculations()
        }
    }

    fun onTestInfoChange(newInfo: TestInfo) {
        _uiState.update { it.copy(testInfo = newInfo) }
    }

    fun onParamsChange(newParams: ClassificationParameters) {
        val llValid = newParams.liquidLimit.isEmpty() || newParams.liquidLimit.toDoubleOrNull() != null
        val plValid = newParams.plasticLimit.isEmpty() || newParams.plasticLimit.toDoubleOrNull() != null
        val weightValid = newParams.initialWeight.isEmpty() || newParams.initialWeight.toDoubleOrNull() != null

        if (llValid && plValid && weightValid) {
            _uiState.update { it.copy(parameters = newParams) }
            performCalculations()
        } else {
            // Optionally, handle the invalid input case, e.g., by only updating the text field without triggering recalculation.
            // For simplicity here, we can just update the state without triggering the calculation if the input is partial/invalid.
            _uiState.update { it.copy(parameters = newParams) }
        }
    }

    fun onSpecificationSelected(spec: Specification?) {
        if (spec?.id == "custom-new") {
            _uiState.update {
                it.copy(
                    selectedSpecification = spec,
                    isCustomSpecEditing = true,
                    showSpecComparison = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedSpecification = spec,
                    isCustomSpecEditing = false,
                    showSpecComparison = spec != null
                )
            }
        }
    }

    fun onSampleTypeChange(newType: SampleType) {
        _uiState.update {
            val newSieves = if (newType == SampleType.SOIL) Sieve.standardSet() else Sieve.aggregateSet()
            it.copy(
                selectedSampleType = newType,
                sieves = newSieves,
                result = null, // Reset results
                customSpecSieves = newSieves, // Update sieves for custom spec form
                customSpecLimits = emptyMap(), // Reset custom limits
                isCustomSpecEditing = false,
                selectedSpecification = null,
                showSpecComparison = false
            )
        }
    }

    fun onCustomSpecLimitChange(sieveOpening: Double, min: String, max: String) {
        _uiState.update { state ->
            val updatedLimits = state.customSpecLimits.toMutableMap()
            updatedLimits[sieveOpening] = Pair(min, max)
            state.copy(customSpecLimits = updatedLimits)
        }
    }

    fun onCustomSpecNameChange(name: String) {
        _uiState.update { it.copy(customSpecName = name) }
    }

    fun saveCustomSpecification(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state.customSpecName.isBlank()) {
                _userMessage.value = context.getString(R.string.error_spec_name_empty)
                return@launch
            }
            if (state.customSpecLimits.isEmpty() || state.customSpecLimits.all { it.value.first.isBlank() && it.value.second.isBlank() }) {
                _userMessage.value = context.getString(R.string.error_spec_limits_empty)
                return@launch
            }

            val limits = state.customSpecLimits.mapNotNull { (opening, minMaxPair) ->
                val min = minMaxPair.first.toFloatOrNull() ?: 0f
                val max = minMaxPair.second.toFloatOrNull() ?: 100f
                if (minMaxPair.first.isNotBlank() || minMaxPair.second.isNotBlank()) {
                    SpecificationLimit(opening, min, max)
                } else {
                    null
                }
            }

            val newSpec = Specification(
                name = state.customSpecName,
                limits = limits,
                isCustom = true
            )
            repository.saveCustomSpecification(newSpec)

            _uiState.update { it.copy(
                isCustomSpecEditing = false,
                customSpecName = "",
                customSpecLimits = emptyMap(),
                selectedSpecification = newSpec
            ) }
            _userMessage.value = context.getString(R.string.success_spec_saved)
        }
    }

    fun deleteCustomSpecification(specId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.selectedSpecification?.id == specId) {
                _uiState.update { it.copy(selectedSpecification = null, showSpecComparison = false) }
            }
            repository.deleteCustomSpecification(specId)
        }
    }

    fun onChartValueSelected(entry: Entry?) {
        if (entry == null) {
            _uiState.update { it.copy(highlightedValue = null) }
            return
        }
        val sieveSize = 10.0.pow(entry.x.toDouble())
        val passing = entry.y
        val message = String.format(Locale.US, "Passing %.2f mm: %.1f%%", sieveSize, passing)
        _uiState.update { it.copy(highlightedValue = message) }
    }

    fun clearHighlightedValue() {
        _uiState.update { it.copy(highlightedValue = null) }
    }


    private fun performCalculations() {
        val state = _uiState.value
        // 1. Get base calculation result
        var analysisResult = SieveAnalysisCalculator.calculate(state.sieves, state.parameters)

        // 2. Perform advanced analysis only if the sample type is Soil
        if (state.selectedSampleType == SampleType.SOIL) {
            val ll = state.parameters.liquidLimit.toFloatOrNull()
            val pl = state.parameters.plasticLimit.toFloatOrNull()

            val classificationResult = if (ll != null && pl != null) {
                AASHTO_USCS_Classifier.classify(analysisResult, state.parameters)
            } else {
                null
            }

            val frostSusceptibility = AASHTO_USCS_Classifier.getFrostSusceptibility(analysisResult.percentFines)

            val predictedProperties = classificationResult?.let {
                AASHTO_USCS_Classifier.predictEngineeringProperties(analysisResult, state.parameters, it.uscs)
            }


            // Combine all soil-specific results
            analysisResult = analysisResult.copy(
                classification = classificationResult,
                frostSusceptibility = frostSusceptibility,
                predictedProperties = predictedProperties
            )
        }

        _uiState.update {
            it.copy(
                sieves = analysisResult.sieves,
                result = analysisResult
            )
        }
    }

    fun saveReport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state.result == null || state.testInfo.boreholeNo.isBlank()) {
                _userMessage.value = context.getString(R.string.error_fill_info_and_compute)
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val report = SieveAnalysisReport(
                id = state.currentReportId ?: UUID.randomUUID().toString(),
                testInfo = state.testInfo,
                parameters = state.parameters,
                sieves = state.sieves,
                result = state.result
            )
            repository.saveSieveAnalysisReport(report)
            _uiState.update { it.copy(isLoading = false, currentReportId = report.id) }
            _userMessage.value = context.getString(R.string.success_report_saved)
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
