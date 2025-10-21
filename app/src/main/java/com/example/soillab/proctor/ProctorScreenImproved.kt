package com.example.soillab.proctor

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Yellow500
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Composable
fun ProctorScreenImproved(
    viewModel: ProctorViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Data Entry", "Results")

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
                0 -> SetupTab(uiState, viewModel)
                1 -> DataEntryTab(uiState, viewModel)
                2 -> ResultsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun SetupTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
    TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)
    TestSetupPanel(
        parameters = uiState.parameters,
        onParamsChange = viewModel::onParamsChange
    )
}

@Composable
fun DataEntryTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
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
}

@Composable
fun ResultsTab(uiState: ProctorUiState, viewModel: ProctorViewModel) {
    AnimatedVisibility(visible = uiState.result != null) {
        uiState.result?.let { result ->
            ResultDashboard(
                result = result,
                fieldMoistureContent = uiState.fieldMoistureContent,
                onFieldMoistureChange = viewModel::onFieldMoistureChange,
                requiredCompaction = uiState.requiredCompaction,
                onRequiredCompactionChange = viewModel::onRequiredCompactionChange,
                testParameters = uiState.parameters
            )
        }
    }
}
