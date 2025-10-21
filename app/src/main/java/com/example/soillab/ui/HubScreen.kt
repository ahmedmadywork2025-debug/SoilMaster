package com.example.soillab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Quick Access Section
        SectionTitle(title = "Quick Access")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            quickAccessItems.forEach { item ->
                QuickAccessCard(item = item, onNavigate = onNavigate)
            }
        }

        // Tests & Procedures Section
        SectionTitle(title = "Tests & Procedures")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(HubRepository.getHubActions()) { action ->
                TestProcedureCard(action = action, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessCard(item: QuickAccessItem, onNavigate: (AppScreen, String?) -> Unit) {
    Card(
        onClick = { onNavigate(item.screen, item.reportId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
            Icon(imageVector = icon, contentDescription = "Status", tint = color)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestProcedureCard(action: HubAction, onNavigate: (AppScreen, String?) -> Unit) {
    val isEnabled = action.screen != null
    Card(
        onClick = { if (isEnabled) onNavigate(action.screen!!, null) },
        enabled = isEnabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = stringResource(id = action.titleResId),
                modifier = Modifier.size(32.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = action.titleResId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

