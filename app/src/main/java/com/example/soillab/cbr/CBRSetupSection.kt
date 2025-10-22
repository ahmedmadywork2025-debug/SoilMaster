package com.example.soillab.cbr

import androidx.compose.runtime.Composable
import com.example.soillab.ui.components.TestInfoSection

@Composable
fun CBRSetupSection(
    viewModel: CBRViewModel,
    uiState: CBRUiState
) {
    TestInfoSection(uiState.parameters.testInfo) { newInfo ->
        viewModel.onParamsChange(uiState.parameters.copy(testInfo = newInfo))
    }
    InputAndParametersSection(viewModel, uiState)
}
