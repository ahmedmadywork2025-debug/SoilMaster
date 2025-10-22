package com.example.soillab.proctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.soillab.ui.components.ActionButtons

@Composable
fun ProctorDataAndResultsSection(
    uiState: ProctorUiState,
    viewModel: ProctorViewModel
) {
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

    AnimatedVisibility(visible = uiState.result != null) {
        if (uiState.result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ResultDashboard(
                result = uiState.result,
                fieldMoistureContent = uiState.fieldMoistureContent,
                onFieldMoistureChange = viewModel::onFieldMoistureChange,
                requiredCompaction = uiState.requiredCompaction,
                onRequiredCompactionChange = viewModel::onRequiredCompactionChange,
                testParameters = uiState.parameters
            )
        }
    }
}
