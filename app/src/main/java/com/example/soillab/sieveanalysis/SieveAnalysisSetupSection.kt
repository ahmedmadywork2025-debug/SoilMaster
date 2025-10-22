package com.example.soillab.sieveanalysis

import androidx.compose.runtime.Composable
import com.example.soillab.ui.components.TestInfoSection

@Composable
fun SieveAnalysisSetupSection(
    viewModel: SieveAnalysisViewModel,
    uiState: SieveUiState
) {
    TestInfoSection(uiState.testInfo, onInfoChange = viewModel::onTestInfoChange)
    ParametersSection(
        params = uiState.parameters,
        onParamsChange = viewModel::onParamsChange,
        sampleType = uiState.selectedSampleType,
        onSampleTypeChange = viewModel::onSampleTypeChange
    )
}
