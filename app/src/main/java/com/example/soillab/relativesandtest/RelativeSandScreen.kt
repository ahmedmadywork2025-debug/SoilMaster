package com.example.soillab.relativesandtest

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 1. ViewModel: لإدارة الحالة والمنطق ---

class RelativeSandViewModel : ViewModel() {

    // States for all input fields
    var sampleName by mutableStateOf("")
    var wetSoilContainer by mutableStateOf("")
    var container by mutableStateOf("")
    var drySoil by mutableStateOf("")
    var volume by mutableStateOf("")
    var maxDensity by mutableStateOf("")
    var minDensity by mutableStateOf("")
    var requiredCompaction by mutableStateOf("")
    var resultText by mutableStateOf("")

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun onClear() {
        sampleName = ""
        wetSoilContainer = ""
        container = ""
        drySoil = ""
        volume = ""
        maxDensity = ""
        minDensity = ""
        requiredCompaction = ""
        resultText = ""
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowToast("All fields cleared"))
        }
    }

    fun onCalculate(context: Context) {
        val WwContainer = wetSoilContainer.toDoubleOrNull()
        val WContainer = container.toDoubleOrNull()
        val Wdry = drySoil.toDoubleOrNull()
        val vol = volume.toDoubleOrNull()
        val gammaMax = maxDensity.toDoubleOrNull()
        val gammaMin = minDensity.toDoubleOrNull()
        val reqCompaction = requiredCompaction.toDoubleOrNull()

        if (listOf(WwContainer, WContainer, Wdry, vol, gammaMax, gammaMin, reqCompaction).any { it == null } || sampleName.isBlank()) {
            resultText = ""
            viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("Please fill all fields with valid numbers.")) }
            return
        }

        if (listOf(WwContainer, WContainer, Wdry, vol, gammaMax, gammaMin, reqCompaction).any { it!! <= 0 }) {
            resultText = ""
            viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("All numeric values must be positive.")) }
            return
        }

        val Wwet = WwContainer!! - WContainer!!
        val moistureContent = ((Wwet - Wdry!!) / Wdry) * 100
        val unitWeight = Wdry / vol!!
        val relativeDensity = ((unitWeight - gammaMin!!) / (gammaMax!! - gammaMin)) * 100

        val resultBuilder = StringBuilder()
        resultBuilder.append(String.format(Locale.US, "Unit Weight of Soil: %.3f g/cc\n", unitWeight))
        resultBuilder.append(String.format(Locale.US, "Relative Density: %.2f%%\n", relativeDensity))
        resultBuilder.append(String.format(Locale.US, "Moisture Content: %.2f%%", moistureContent))

        if (relativeDensity < reqCompaction!!) {
            resultBuilder.append("\n\n⚠️ Relative Density is below Required Compaction!")
        }
        resultText = resultBuilder.toString()

        val reportData = PdfReportData(
            sampleName = sampleName,
            WwContainer = WwContainer, WContainer = WContainer, Wdry = Wdry, volume = vol,
            gammaMax = gammaMax, gammaMin = gammaMin, requiredCompaction = reqCompaction,
            unitWeight = unitWeight, relativeDensity = relativeDensity, moistureContent = moistureContent
        )
        PdfGenerator.savePdfReport(context, reportData)
    }

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
    }
}


// --- 2. UI: واجهة المستخدم الكاملة ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelativeSandScreen(viewModel: RelativeSandViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            if (event is RelativeSandViewModel.UiEvent.ShowToast) {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relative Density Calculator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InputTextField(
                value = viewModel.sampleName,
                onValueChange = { viewModel.sampleName = it },
                label = "Sample Name",
                keyboardType = KeyboardType.Text
            )
            InputTextField(viewModel.wetSoilContainer, { viewModel.wetSoilContainer = it }, "Wet Soil + Container (g)")
            InputTextField(viewModel.container, { viewModel.container = it }, "Container (g)")
            InputTextField(viewModel.drySoil, { viewModel.drySoil = it }, "Dry Soil (g)")
            InputTextField(viewModel.volume, { viewModel.volume = it }, "Volume (cc)")
            InputTextField(viewModel.maxDensity, { viewModel.maxDensity = it }, "Max Density (g/cc)")
            InputTextField(viewModel.minDensity, { viewModel.minDensity = it }, "Min Density (g/cc)")
            InputTextField(viewModel.requiredCompaction, { viewModel.requiredCompaction = it }, "Required Compaction (%)")

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { viewModel.onCalculate(context) }, Modifier.weight(1f)) {
                    Text("CALCULATE & SAVE")
                }
                OutlinedButton(onClick = { viewModel.onClear() }, Modifier.weight(1f)) {
                    Text("CLEAR")
                }
            }

            if (viewModel.resultText.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = viewModel.resultText,
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun InputTextField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType = KeyboardType.Decimal) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        singleLine = true
    )
}

// --- 3. PDF Generator: منطق إنشاء وحفظ PDF ---

private data class PdfReportData(
    val sampleName: String, val WwContainer: Double, val WContainer: Double, val Wdry: Double,
    val volume: Double, val gammaMax: Double, val gammaMin: Double, val requiredCompaction: Double,
    val unitWeight: Double, val relativeDensity: Double, val moistureContent: Double
)

private object PdfGenerator {
    fun savePdfReport(context: Context, data: PdfReportData) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = TextPaint().apply {
            textSize = 20f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val textPaint = TextPaint().apply { textSize = 12f; isAntiAlias = true; color = Color.BLACK }
        val boldText = TextPaint(textPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerBg = Paint().apply { style = Paint.Style.FILL; color = Color.LTGRAY }
        val linePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK }

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())

        canvas.drawText("Relative Density Test Report", pageInfo.pageWidth / 2f, 60f, titlePaint)
        canvas.drawText("Date & Time: $currentTime", 40f, 90f, textPaint)
        canvas.drawText("Sample Name: ${data.sampleName}", 40f, 110f, boldText)
        canvas.drawLine(40f, 120f, pageInfo.pageWidth - 40f, 120f, linePaint)

        var startY = 140f
        val startX = 40f
        val tableWidth = pageInfo.pageWidth - 80f
        val col1W = tableWidth * 0.6f
        val col2W = tableWidth * 0.4f

        val rows = listOf(
            "Wet Soil + Container (g)" to fmt(data.WwContainer), "Container (g)" to fmt(data.WContainer),
            "Dry Soil (g)" to fmt(data.Wdry), "Volume (cc)" to fmt(data.volume),
            "Max Density (g/cc)" to fmt(data.gammaMax), "Min Density (g/cc)" to fmt(data.gammaMin),
            "Required Compaction (%)" to fmt(data.requiredCompaction), "Unit Weight (g/cc)" to fmt(data.unitWeight),
            "Relative Density (%)" to fmt(data.relativeDensity), "Moisture Content (%)" to fmt(data.moistureContent)
        )

        // Draw Table Header
        canvas.drawRect(startX, startY, startX + tableWidth, startY + 30f, headerBg)
        canvas.drawText("Parameter", startX + 10f, startY + 20f, boldText)
        canvas.drawText("Value", startX + col1W + 10f, startY + 20f, boldText)
        startY += 30f

        // Draw Rows
        rows.forEach { (label, value) ->
            val textLayout = StaticLayout.Builder.obtain(label, 0, label.length, textPaint, col1W.toInt() - 20).build()
            val rowHeight = maxOf(40f, textLayout.height + 20f)

            canvas.drawRect(startX, startY, startX + tableWidth, startY + rowHeight, linePaint)
            canvas.drawLine(startX + col1W, startY, startX + col1W, startY + rowHeight, linePaint)

            canvas.save()
            canvas.translate(startX + 10f, startY + (rowHeight - textLayout.height) / 2)
            textLayout.draw(canvas)
            canvas.restore()

            canvas.drawText(value, startX + col1W + 10f, startY + rowHeight / 2 + 5f, textPaint)
            startY += rowHeight
        }

        startY += 40f
        canvas.drawText("Engineer Signature: ______________________", startX, startY, boldText)

        doc.finishPage(page)
        val fileName = "RelativeDensity_${data.sampleName.replace("\\s+".toRegex(), "_")}.pdf"
        saveFile(context, fileName, doc)
        doc.close()
    }

    private fun saveFile(context: Context, fileName: String, doc: PdfDocument) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            Uri.fromFile(File(downloadsDir, fileName))
        }

        try {
            uri?.let {
                resolver.openOutputStream(it)?.use { stream -> doc.writeTo(stream) }
                Toast.makeText(context, "Report saved to Downloads", Toast.LENGTH_LONG).show()
            } ?: throw Exception("Failed to create MediaStore entry.")
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fmt(d: Double): String = String.format(Locale.US, "%.3f", d)
}
