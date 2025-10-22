package com.example.soillab.proctor

import androidx.compose.runtime.Composable
import com.example.soillab.ui.components.TestInfoSection

@Composable
fun ProctorSetupSection(
    uiState: ProctorUiState,
    viewModel: ProctorViewModel
) {
    TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)
    TestSetupPanel(
        parameters = uiState.parameters,
        onParamsChange = viewModel::onParamsChange
    )
}
