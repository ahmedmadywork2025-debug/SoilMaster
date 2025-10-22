package com.example.soillab.liquidlimittest

import androidx.compose.runtime.Composable
import com.example.soillab.ui.components.TestInfoSection

@Composable
fun AtterbergLimitsSetupSection(
    viewModel: AtterbergCoreViewModel,
    uiState: AtterbergUiState
) {
    TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)
}
