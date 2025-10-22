package com.example.soillab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soillab.AppScreen
import com.example.soillab.R

data class HubAction(
    val titleResId: Int,
    val screen: AppScreen?,
    val icon: ImageVector
)

object HubRepository {
    fun getHubActions(): List<HubAction> {
        return listOf(
            HubAction(R.string.hub_cbr, AppScreen.CBR_TEST, Icons.Default.Compress),
            HubAction(R.string.hub_atterberg, AppScreen.ATTERBERG_LIMITS_TEST, Icons.Default.WaterDrop),
            HubAction(R.string.hub_sieve, AppScreen.SIEVE_ANALYSIS, Icons.Default.Grain),
            HubAction(R.string.hub_proctor, AppScreen.PROCTOR_TEST, Icons.Default.LineWeight), // Activated
            HubAction(R.string.hub_field_density, null, Icons.Default.Grass), // Placeholder
            HubAction(R.string.hub_drilling_log, null, Icons.Default.Layers) // Placeholder
        )
    }
}

data class QuickAccessItem(
    val title: String,
    val subtitle: String,
    val status: String,
    val isPass: Boolean?,
    val reportId: String,
    val screen: AppScreen
)

@Composable
fun HubScreen(onNavigate: (AppScreen, String?) -> Unit) {
    val quickAccessItems = listOf(
        QuickAccessItem("CBR Report - Project Alpha", "Status: Pass", "Draft", true, "1", AppScreen.CBR_TEST),
        QuickAccessItem("Sieve Analysis - Site Beta", "Status: Warning", "Draft", false, "2", AppScreen.SIEVE_ANALYSIS)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Header()
        }

        item {
            SectionTitle(title = "Quick Access")
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                quickAccessItems.forEach { item ->
                    QuickAccessCard(item = item, onNavigate = onNavigate)
                }
            }
        }

        item {
            SectionTitle(title = "Tests & Procedures")
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(300.dp) // Adjust height as needed
            ) {
                items(HubRepository.getHubActions()) { action ->
                    TestProcedureCard(action = action, onNavigate = onNavigate)
                }
            }
        }
    }
}

@Composable
fun Header() {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = "Welcome back,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "GeoMind Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessCard(item: QuickAccessItem, onNavigate: (AppScreen, String?) -> Unit) {
    Card(
        onClick = { onNavigate(item.screen, item.reportId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val (icon, color) = when (item.isPass) {
                true -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                false -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                null -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(imageVector = icon, contentDescription = "Status", tint = color, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestProcedureCard(action: HubAction, onNavigate: (AppScreen, String?) -> Unit) {
    val isEnabled = action.screen != null
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        onClick = { if (isEnabled) onNavigate(action.screen!!, null) },
        enabled = isEnabled,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    if (isEnabled) Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            containerColor
                        )
                    ) else Brush.verticalGradient(colors = listOf(containerColor, containerColor))
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = stringResource(id = action.titleResId),
                modifier = Modifier.size(40.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = action.titleResId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                textAlign = TextAlign.Center
            )
        }
    }
}