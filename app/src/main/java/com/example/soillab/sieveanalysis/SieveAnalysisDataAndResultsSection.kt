package com.example.soillab.sieveanalysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SieveAnalysisDataAndResultsSection(
    viewModel: SieveAnalysisViewModel,
    uiState: SieveUiState
) {
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

    AnimatedVisibility(visible = uiState.result != null) {
        if (uiState.result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ResultDashboard(uiState.result, uiState, viewModel)
        }
    }
}
