package com.example.soillab.liquidlimittest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.soillab.R
import com.example.soillab.ui.components.ActionButtons
import com.example.soillab.ui.components.DataPanel

@Composable
fun AtterbergLimitsDataAndResultsSection(
    viewModel: AtterbergCoreViewModel,
    uiState: AtterbergUiState
) {
    val context = LocalContext.current
    DataPanel(stringResource(R.string.liquid_limit_input)) { EnhancedLLSampleInputSection(uiState.llSamples, uiState.llValidation, viewModel::onLLSampleValueChange, viewModel::addLLSample, viewModel::removeLLSample) }
    DataPanel(stringResource(R.string.plastic_limit_input)) { PLSampleInputSection(uiState.plSamples, uiState.plValidation, viewModel::onPLSampleValueChange, viewModel::addPLSample, viewModel::removePLSample) }
    ActionButtons(onCompute = viewModel::performAdvancedCalculations, onLoadExample = { viewModel.loadExampleData(context) })

    AnimatedVisibility(visible = uiState.calculationResult != null) {
        if (uiState.calculationResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AdvancedAnalysisDashboard(uiState.calculationResult)
        }
    }
}
