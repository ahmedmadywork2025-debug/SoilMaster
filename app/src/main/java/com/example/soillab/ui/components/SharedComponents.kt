package com.example.soillab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soillab.R
import com.example.soillab.data.TestInfo
import com.example.soillab.data.ValidationResult
import com.example.soillab.ui.theme.Green500
import com.example.soillab.ui.theme.Yellow500
import java.util.Locale

/**
 * دالة ملحقة لتحويل الأرقام العربية-الهندية في نص إلى أرقام لاتينية (إنجليزية).
 */
private fun String.toEnglishNumerals(): String {
    return this
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
        .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
}

@Composable
fun DataPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), Color.Transparent)), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "//$title", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        content()
    }
}

@Composable
fun InfoPanel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .border(1.dp, Yellow500.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(Yellow500.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Yellow500)
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Yellow500)
    }
}

@Composable
fun NeuralInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Decimal, isError: Boolean = false) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val onFilteredValueChange: (String) -> Unit = { newText ->
        val englishNumeralsText = newText.toEnglishNumerals()
        if (keyboardType == KeyboardType.Decimal) {
            val filteredText = englishNumeralsText.filter { it.isDigit() || it == '.' || it == '-' }
            if (filteredText.count { it == '.' } <= 1 && filteredText.count { it == '-' } <= 1 && (filteredText.startsWith('-') || !filteredText.contains('-'))) {
                onValueChange(filteredText)
            }
        } else {
            onValueChange(englishNumeralsText)
        }
    }

    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        BasicTextField(
            value = value,
            onValueChange = onFilteredValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource
        )
        val lineColor = when { isError -> MaterialTheme.colorScheme.error; isFocused -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) }
        Divider(color = lineColor, thickness = if (isFocused || isError) 2.dp else 1.dp)
    }
}

@Composable
fun ResultDisplay(title: String, value: String, isPrimary: Boolean = false, valueColor: Color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = if (isPrimary) 20.sp else 16.sp)
    }
}

@Composable
fun ResultDisplayWithInfo(
    title: String,
    value: String,
    infoTitleResId: Int?,
    infoContentResId: Int?,
    isPrimary: Boolean = false,
    valueColor: Color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog && infoTitleResId != null && infoContentResId != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(infoTitleResId), color = MaterialTheme.colorScheme.primary) },
            text = { Text(stringResource(infoContentResId), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (infoContentResId != null) {
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = stringResource(R.string.more_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = if (isPrimary) 20.sp else 16.sp)
    }
}

@Composable
fun ValidationIndicator(validation: ValidationResult?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val (icon: ImageVector, color: Color) = when {
            validation == null -> Icons.Default.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
            validation.isValid -> Icons.Default.CheckCircle to Green500
            else -> Icons.Default.Error to MaterialTheme.colorScheme.error
        }
        Icon(icon, "Validation Status", tint = color)
    }
}

@Composable
fun InsightCard(icon: ImageVector, title: String, content: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier
            .padding(end = 12.dp)
            .size(20.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            Text(content, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
fun TestInfoSection(info: TestInfo, onInfoChange: (TestInfo) -> Unit) {
    DataPanel(stringResource(R.string.project_identifiers)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuralInput(info.boreholeNo, { onInfoChange(info.copy(boreholeNo = it)) }, stringResource(R.string.location_bh), Modifier.weight(1f), keyboardType = KeyboardType.Text)
            NeuralInput(info.sampleNo, { onInfoChange(info.copy(sampleNo = it)) }, stringResource(R.string.sample_no), Modifier.weight(1f), keyboardType = KeyboardType.Text)
        }
        NeuralInput(info.sampleDescription, { onInfoChange(info.copy(sampleDescription = it)) }, stringResource(R.string.sample_description), Modifier.fillMaxWidth(), keyboardType = KeyboardType.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuralInput(info.testedBy, { onInfoChange(info.copy(testedBy = it)) }, stringResource(R.string.tested_by), Modifier.weight(1f), keyboardType = KeyboardType.Text)
            NeuralInput(info.checkedBy, { onInfoChange(info.copy(checkedBy = it)) }, stringResource(R.string.checked_by), Modifier.weight(1f), keyboardType = KeyboardType.Text)
        }
    }
}

/**
 * مكون موحد لأزرار الإجراءات الرئيسية.
 */
@Composable
fun ActionButtons(onCompute: () -> Unit, onLoadExample: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onLoadExample,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.load_example))
        }
        Button(onClick = onCompute, modifier = Modifier.weight(1.5f)) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.compute))
        }
    }
}

@Composable
fun MoistureContentCalculatorDialog(onDismiss: () -> Unit, onCalculate: (String) -> Unit) {
    var canWetSoil by remember { mutableStateOf("") }
    var canDrySoil by remember { mutableStateOf("") }
    var canWeight by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Moisture Content Calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuralInput(value = canWetSoil, onValueChange = { canWetSoil = it }, label = "Can + Wet Soil (g)")
                NeuralInput(value = canDrySoil, onValueChange = { canDrySoil = it }, label = "Can + Dry Soil (g)")
                NeuralInput(value = canWeight, onValueChange = { canWeight = it }, label = "Can Weight (g)")
                result?.let {
                    Text("Result: $it%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val m1 = canWetSoil.toDoubleOrNull()
                val m2 = canDrySoil.toDoubleOrNull()
                val mc = canWeight.toDoubleOrNull()
                if (m1 != null && m2 != null && mc != null && (m2 - mc) > 0) {
                    val moisture = ((m1 - m2) / (m2 - mc)) * 100
                    val resultString = String.format(Locale.US, "%.2f", moisture)
                    result = resultString
                    onCalculate(resultString)
                }
            }) {
                Text(stringResource(R.string.compute))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

