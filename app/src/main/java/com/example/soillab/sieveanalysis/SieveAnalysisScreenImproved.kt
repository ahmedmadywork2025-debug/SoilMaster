package com.example.soillab.sieveanalysis

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
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
        _uiState.update { state ->
            val updatedSieves = state.sieves.map {
                if (it.opening == opening) it.copy(retainedWeight = retainedWeight) else it
            }
            state.copy(sieves = updatedSieves)
        }
        performCalculations()
    }

    fun onTestInfoChange(newInfo: TestInfo) {
        _uiState.update { it.copy(testInfo = newInfo) }
    }

    fun onParamsChange(newParams: ClassificationParameters) {
        _uiState.update { it.copy(parameters = newParams) }
        performCalculations()
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
@Composable
fun SieveAnalysisScreenImproved(
    viewModel: SieveAnalysisViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Data Entry", "Analysis & Results")

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
                0 -> SetupTab(viewModel, uiState)
                1 -> DataEntryTab(viewModel, uiState)
                2 -> AnalysisAndResultsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun SetupTab(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    TestInfoSection(uiState.testInfo, onInfoChange = viewModel::onTestInfoChange)
    ParametersSection(
        params = uiState.parameters,
        onParamsChange = viewModel::onParamsChange,
        sampleType = uiState.selectedSampleType,
        onSampleTypeChange = viewModel::onSampleTypeChange
    )
}

@Composable
fun DataEntryTab(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    val context = LocalContext.current
    SieveDataTable(sieves = uiState.sieves, onSieveWeightChange = viewModel::onSieveWeightChange)
    AnimatedVisibility(visible = uiState.isCustomSpecEditing) {
        CustomSpecEditorPanel(
            sieves = uiState.customSpecSieves,
            limits = uiState.customSpecLimits,
            onLimitChange = viewModel::onCustomSpecLimitChange,
            specName = uiState.customSpecName,
            onNameChange = viewModel::onCustomSpecNameChange,
            onSave = { viewModel.saveCustomSpecification(context) }
        )
    }
}

@Composable
fun AnalysisAndResultsTab(uiState: SieveUiState, viewModel: SieveAnalysisViewModel) {
    AnimatedVisibility(visible = uiState.result != null) {
        uiState.result?.let { ResultDashboard(it, uiState, viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametersSection(
    params: ClassificationParameters,
    onParamsChange: (ClassificationParameters) -> Unit,
    sampleType: SampleType,
    onSampleTypeChange: (SampleType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    DataPanel(stringResource(R.string.sieve_classification_params)) {
        // Dropdown for Sample Type
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = stringResource(id = sampleType.displayNameResId),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.sample_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SampleType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.displayNameResId)) },
                        onClick = {
                            onSampleTypeChange(type)
                            expanded = false
                        }
                    )
                }
            }
        }

        NeuralInput(
            value = params.initialWeight,
            onValueChange = { onParamsChange(params.copy(initialWeight = it)) },
            label = stringResource(R.string.sieve_initial_weight_g),
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(visible = sampleType == SampleType.SOIL) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuralInput(
                        value = params.liquidLimit,
                        onValueChange = { onParamsChange(params.copy(liquidLimit = it)) },
                        label = stringResource(R.string.liquid_limit),
                        modifier = Modifier.weight(1f)
                    )
                    NeuralInput(
                        value = params.plasticLimit,
                        onValueChange = { onParamsChange(params.copy(plasticLimit = it)) },
                        label = stringResource(R.string.plastic_limit),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(R.string.info_sieve_automatic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SieveDataTable(sieves: List<Sieve>, onSieveWeightChange: (Double, String) -> Unit) {
    DataPanel(stringResource(R.string.sieve_retained_weights)) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.sieve_header_name), modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sieve_header_retained), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sieve_header_cum_retained), modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, lineHeight = 12.sp)
            Text(stringResource(R.string.sieve_header_passing), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))

        // Data Rows
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sieves.forEach { sieve ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min)
                ) {
                    Text(
                        text = sieve.name,
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )

                    NeuralInput(
                        value = sieve.retainedWeight,
                        onValueChange = { onSieveWeightChange(sieve.opening, it) },
                        label = "",
                        modifier = Modifier.weight(1.5f)
                    )

                    val cumRetainedPercent = 100.0 - sieve.percentPassing
                    val showResults = sieves.any { it.cumulativeRetained > 0.0 } || (sieve.opening == 0.0 && sieve.retainedWeight.isNotBlank())

                    Text(
                        text = if (showResults) String.format(Locale.US, "%.1f", cumRetainedPercent) else "-",
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(end = 4.dp)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (showResults || sieve.opening == 0.0) String.format(Locale.US, "%.1f", sieve.percentPassing) else "-",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
fun ResultDashboard(result: SieveAnalysisResult, uiState: SieveUiState, viewModel: SieveAnalysisViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(visible = uiState.selectedSampleType == SampleType.SOIL && result.classification != null) {
            result.classification?.let { ClassificationResultSection(it, result) }
        }
        AnimatedVisibility(visible = uiState.selectedSampleType == SampleType.SOIL && result.predictedProperties != null) {
            result.predictedProperties?.let { PredictionPanel(it) }
        }
        GradationChartSection(
            result = result,
            uiState = uiState,
            onValueSelected = viewModel::onChartValueSelected,
            onSpecSelected = viewModel::onSpecificationSelected,
            onDeleteSpec = { specId -> viewModel.deleteCustomSpecification(specId) }
        )
        SummarySection(result, uiState.selectedSampleType)
    }
}

@Composable
fun PredictionPanel(predictions: PredictedProperties) {
    DataPanel(title = stringResource(id = R.string.prediction_panel_title)) {
        predictions.predictedOmc?.let {
            ResultDisplayWithInfo(
                title = stringResource(R.string.predicted_omc),
                value = "~ ${String.format(Locale.US, "%.1f", it)}%",
                infoTitleResId = R.string.info_prediction_title,
                infoContentResId = R.string.info_prediction_content
            )
        }
        predictions.predictedMdd?.let {
            ResultDisplayWithInfo(
                title = stringResource(R.string.predicted_mdd),
                value = "~ ${String.format(Locale.US, "%.2f", it)} t/m³",
                infoTitleResId = R.string.info_prediction_title,
                infoContentResId = R.string.info_prediction_content
            )
        }
        predictions.predictedCbr?.let {
            ResultDisplayWithInfo(
                title = stringResource(R.string.predicted_cbr),
                value = "~ ${String.format(Locale.US, "%.0f", it)}%",
                infoTitleResId = R.string.info_prediction_title,
                infoContentResId = R.string.info_prediction_content
            )
        }
    }
}


@Composable
fun ClassificationResultSection(classification: SoilClassificationResult, result: SieveAnalysisResult) {
    DataPanel("GeoMind 3025 // Dual Classification") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AASHTO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(classification.aashto.groupName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("GI = ${classification.aashto.groupIndex}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider(modifier = Modifier
                .height(60.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("USCS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(classification.uscs.groupName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(classification.uscs.groupDescriptionResId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        InsightCard(icon = Icons.Default.Comment, title = stringResource(R.string.sieve_ai_commentary), content = stringResource(classification.aiCommentaryResId))
        result.frostSusceptibility?.let {
            val color = try { Color(android.graphics.Color.parseColor(it.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurfaceVariant }
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AcUnit, contentDescription = "Frost", tint = MaterialTheme.colorScheme.primary, modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp))
                Text(stringResource(R.string.frost_susceptibility), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(it.resId), color = color, fontWeight = FontWeight.Bold)
            }
        }
        result.estimatedPermeability?.let {
            InsightCard(icon = Icons.Default.WaterDrop, title = stringResource(R.string.est_permeability), content = "${String.format(Locale.US, "%.2E", it)} cm/s (Hazen)")
        }
        InsightCard(icon = Icons.Default.Checklist, title = stringResource(R.string.sieve_ai_recommendation), content = stringResource(classification.recommendationResId))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecCompareSection(
    allSpecs: List<Specification>,
    selectedSpec: Specification?,
    onSpecSelected: (Specification?) -> Unit,
    onDeleteSpec: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.spec_compare_title), modifier = Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedSpec?.let {
                    if (it.isCustom) it.name else stringResource(it.nameResId)
                } ?: stringResource(R.string.spec_select),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .weight(2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.spec_none)) },
                    onClick = {
                        onSpecSelected(null)
                        expanded = false
                    }
                )

                val predefined = allSpecs.filter { !it.isCustom }
                val custom = allSpecs.filter { it.isCustom }

                predefined.forEach { spec ->
                    DropdownMenuItem(
                        text = { Text(stringResource(spec.nameResId)) },
                        onClick = {
                            onSpecSelected(spec)
                            expanded = false
                        }
                    )
                }

                if (custom.isNotEmpty()) {
                    Divider()
                    custom.forEach { spec ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(spec.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onDeleteSpec(spec.id); expanded = false }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete Specification",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSpecSelected(spec)
                                expanded = false
                            }
                        )
                    }
                }

                Divider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.spec_custom),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        onSpecSelected(Specification(id = "custom-new", name = "", limits = emptyList(), isCustom = true, nameResId = R.string.spec_custom))
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun CustomSpecEditorPanel(
    sieves: List<Sieve>,
    limits: Map<Double, Pair<String, String>>,
    onLimitChange: (sieveOpening: Double, min: String, max: String) -> Unit,
    specName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit
) {
    DataPanel(title = stringResource(id = R.string.custom_spec_panel_title)) {
        // Name input and save button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeuralInput(
                value = specName,
                onValueChange = { newText -> onNameChange(newText.filter { it.isLetter() || it.isWhitespace() }) },
                label = stringResource(R.string.spec_name_label),
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Text
            )
            Button(onClick = onSave, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_spec))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row {
                Text(
                    text = stringResource(R.string.sieve_header_name),
                    modifier = Modifier.weight(1.5f),
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.min_passing),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.max_passing),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
            }

            sieves.filter { it.opening > 0.0 }.forEach { sieve ->
                val currentLimits = limits[sieve.opening] ?: Pair("", "")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(sieve.name, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onBackground)
                    NeuralInput(
                        value = currentLimits.first,
                        onValueChange = { newMin -> onLimitChange(sieve.opening, newMin, currentLimits.second) },
                        label = "",
                        modifier = Modifier.weight(1f)
                    )
                    NeuralInput(
                        value = currentLimits.second,
                        onValueChange = { newMax -> onLimitChange(sieve.opening, currentLimits.first, newMax) },
                        label = "",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}


@Composable
fun GradationChartSection(
    result: SieveAnalysisResult,
    uiState: SieveUiState,
    onValueSelected: (Entry?) -> Unit,
    onSpecSelected: (Specification?) -> Unit,
    onDeleteSpec: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    DataPanel(stringResource(R.string.sieve_gradation_curve)) {
        SpecCompareSection(
            allSpecs = uiState.allSpecifications,
            selectedSpec = uiState.selectedSpecification,
            onSpecSelected = onSpecSelected,
            onDeleteSpec = onDeleteSpec,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    setupGradationChart(this, colorScheme)
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            onValueSelected(e)
                        }
                        override fun onNothingSelected() {
                            onValueSelected(null)
                        }
                    })
                }
            },
            update = { chart ->
                // Safety check
                if (result.sieves.isEmpty()) {
                    chart.clear()
                    chart.invalidate()
                    return@AndroidView
                }

                val entries = result.sieves
                    .filter { it.opening > 0 }
                    .map { Entry(log10(it.opening).toFloat(), it.percentPassing.toFloat()) }
                    .sortedBy { it.x }

                if (entries.size < 2) {
                    chart.clear()
                    chart.invalidate()
                    return@AndroidView
                }

                val mainDataSet = LineDataSet(entries, "Particle Size Distribution").apply {
                    color = colorScheme.primary.toArgb()
                    lineWidth = 2.5f
                    setDrawCircles(true)
                    setCircleColor(colorScheme.primary.toArgb())
                    circleRadius = 4f
                    setDrawValues(false)
                }
                val dataSets = mutableListOf<ILineDataSet>(mainDataSet)

                if (uiState.selectedSampleType == SampleType.SOIL) {
                    // Highlight D-points
                    result.d10?.let {
                        val entry = Entry(log10(it).toFloat(), 10f)
                        val ds = LineDataSet(listOf(entry), "D10").apply { setupDPointStyle(this, colorScheme.primary.toArgb()) }
                        dataSets.add(ds)
                    }
                    result.d30?.let {
                        val entry = Entry(log10(it).toFloat(), 30f)
                        val ds = LineDataSet(listOf(entry), "D30").apply { setupDPointStyle(this, colorScheme.primary.toArgb()) }
                        dataSets.add(ds)
                    }
                    result.d60?.let {
                        val entry = Entry(log10(it).toFloat(), 60f)
                        val ds = LineDataSet(listOf(entry), "D60").apply { setupDPointStyle(this, colorScheme.primary.toArgb()) }
                        dataSets.add(ds)
                    }
                }


                // Specification bands
                if (uiState.showSpecComparison) {
                    val specLimits: List<SpecificationLimit>? = if (uiState.isCustomSpecEditing) {
                        // Build limits from the custom map
                        uiState.customSpecLimits.mapNotNull { (opening, minMaxPair) ->
                            val min = minMaxPair.first.toFloatOrNull() ?: 0f
                            val max = minMaxPair.second.toFloatOrNull() ?: 100f
                            if (minMaxPair.first.isNotBlank() || minMaxPair.second.isNotBlank()) {
                                SpecificationLimit(opening, min, max)
                            } else {
                                null
                            }
                        }
                    } else {
                        uiState.selectedSpecification?.limits
                    }

                    specLimits?.let { limits ->
                        if (limits.isNotEmpty()) {
                            val lowerEntries = limits.map { Entry(log10(it.sieveOpening).toFloat(), it.minPassing) }.sortedBy { it.x }
                            val upperEntries = limits.map { Entry(log10(it.sieveOpening).toFloat(), it.maxPassing) }.sortedBy { it.x }

                            val lowerDS = LineDataSet(lowerEntries, "Lower Limit").apply {
                                color = Yellow500.toArgb(); lineWidth = 1f; setDrawCircles(false); setDrawValues(false)
                            }
                            val upperDS = LineDataSet(upperEntries, "Upper Limit").apply {
                                color = Yellow500.toArgb(); lineWidth = 1f; setDrawCircles(false); setDrawValues(false)
                                fillColor = Yellow500.toArgb(); fillAlpha = 40; setDrawFilled(true)
                                fillFormatter = IFillFormatter { _, _ -> chart.axisLeft.axisMinimum }
                            }
                            dataSets.add(upperDS) // Add upper first for fill to work
                            dataSets.add(lowerDS)
                        }
                    }
                }


                chart.data = LineData(dataSets)
                chart.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

@Composable
fun SummarySection(result: SieveAnalysisResult, sampleType: SampleType) {
    DataPanel(stringResource(R.string.sieve_summary_properties)) {
        ResultDisplay(title = stringResource(R.string.sieve_percent_gravel), value = "${String.format(Locale.US, "%.1f", result.percentGravel)} %%")
        ResultDisplay(title = stringResource(R.string.sieve_percent_sand), value = "${String.format(Locale.US, "%.1f", result.percentSand)} %%")
        ResultDisplay(title = stringResource(R.string.sieve_percent_fines), value = "${String.format(Locale.US, "%.1f", result.percentFines)} %%")

        AnimatedVisibility(visible = sampleType == SampleType.SOIL) {
            Column {
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                ResultDisplayWithInfo(
                    title = "D10",
                    value = result.d10?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    infoTitleResId = R.string.info_d10_title,
                    infoContentResId = R.string.info_d10_content
                )
                ResultDisplayWithInfo(
                    title = "D30",
                    value = result.d30?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    infoTitleResId = R.string.info_d30_title,
                    infoContentResId = R.string.info_d30_content
                )
                ResultDisplayWithInfo(
                    title = "D60",
                    value = result.d60?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    infoTitleResId = R.string.info_d60_title,
                    infoContentResId = R.string.info_d60_content
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                ResultDisplayWithInfo(
                    title = stringResource(R.string.sieve_cu),
                    value = result.cu?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    infoTitleResId = R.string.info_cu_title,
                    infoContentResId = R.string.info_cu_content
                )
                ResultDisplayWithInfo(
                    title = stringResource(R.string.sieve_cc),
                    value = result.cc?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
                    infoTitleResId = R.string.info_cc_title,
                    infoContentResId = R.string.info_cc_content
                )
                ResultDisplayWithInfo(
                    title = stringResource(R.string.sieve_fineness_modulus),
                    value = String.format(Locale.US, "%.2f", result.finenessModulus),
                    infoTitleResId = R.string.info_fm_title,
                    infoContentResId = R.string.info_fm_content
                )
            }
        }


        result.materialLossPercentage?.let {
            val isAcceptable = abs(it) < 0.5f
            ResultDisplay(
                title = stringResource(R.string.sieve_material_loss),
                value = "${String.format(Locale.US, "%.2f", it)} %%",
                valueColor = if(isAcceptable) Green500 else MaterialTheme.colorScheme.error
            )
        }
    }
}


fun setupDPointStyle(dataSet: LineDataSet, color: Int) {
    dataSet.color = Color.Transparent.toArgb()
    dataSet.setCircleColor(color)
    dataSet.circleRadius = 5f
    dataSet.setDrawValues(true)
    dataSet.valueTextColor = color
    dataSet.valueTextSize = 10f
}

fun setupGradationChart(chart: LineChart, colorScheme: ColorScheme) {
    chart.description.isEnabled = false
    chart.axisRight.isEnabled = false
    chart.legend.textColor = colorScheme.onSurfaceVariant.toArgb()
    chart.setBackgroundColor(Color.Transparent.toArgb())
    chart.setNoDataText("Calculate results to see the gradation curve.")
    chart.setNoDataTextColor(colorScheme.onSurfaceVariant.toArgb())

    chart.axisLeft.apply {
        textColor = colorScheme.onSurfaceVariant.toArgb()
        gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
        axisLineColor = colorScheme.onSurfaceVariant.toArgb()
        axisMinimum = 0f
        axisMaximum = 100f
    }

    chart.xAxis.apply {
        textColor = colorScheme.onSurfaceVariant.toArgb()
        gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
        axisLineColor = colorScheme.onSurfaceVariant.toArgb()
        position = XAxis.XAxisPosition.BOTTOM

        val allSieves = (Sieve.standardSet() + Sieve.aggregateSet()).distinctBy { it.opening }
        val openings = allSieves.filter { it.opening > 0 }.map { it.opening.toFloat() }
        val logOpenings = openings.map { log10(it) }

        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val closestLog = logOpenings.minByOrNull { abs(it - value) }
                val originalOpening = openings.getOrNull(logOpenings.indexOf(closestLog)) ?: 0f

                return when {
                    originalOpening >= 1 -> String.format(Locale.US, "%.0f", originalOpening)
                    originalOpening >= 0.1 -> String.format(Locale.US, "%.2f", originalOpening)
                    else -> String.format(Locale.US, "%.3f", originalOpening)
                }
            }
        }
    }
}
