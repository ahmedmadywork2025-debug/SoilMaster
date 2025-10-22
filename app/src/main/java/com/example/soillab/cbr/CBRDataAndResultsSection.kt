package com.example.soillab.cbr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.soillab.ui.components.ActionButtons

@Composable
fun CBRDataAndResultsSection(
    viewModel: CBRViewModel,
    uiState: CBRUiState
) {
    val context = LocalContext.current
    DataPointsList(uiState.points, viewModel::removePoint)
    CbrChart(
        points = uiState.points,
        correctedPoints = uiState.correctedPoints,
        onValueSelected = viewModel::onChartValueSelected,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
    Spacer(Modifier.height(16.dp))
    ActionButtons(onCompute = viewModel::computeCBR, onLoadExample = { viewModel.loadExampleData(context) })

    AnimatedVisibility(visible = uiState.result != null) {
        val result = uiState.result
        if (result != null) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                ResultSection(result, uiState.requiredCbr, viewModel::onRequiredCbrChange)
                if (result.insights != null) {
                    EngineeringPropertiesPanel(result.insights!!, result)
                }
            }
        }
    }
}
