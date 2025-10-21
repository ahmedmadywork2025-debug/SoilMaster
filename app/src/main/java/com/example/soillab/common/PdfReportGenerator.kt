package com.example.soillab.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.widget.Toast
import androidx.core.net.toUri
import com.example.soillab.data.ReportHeaderData
import java.io.File

/**
 * A centralized, professional PDF report generator.
 * This object can be used by any test to create a standardized report
 * with a consistent header and footer.
 */
object PdfReportGenerator {

    // --- Paints and Constants ---
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 30f

    // Reusable Paint objects for different text styles
    private val P_HEADER_EN = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val P_HEADER_AR = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.RIGHT }
    private val P_PROJECT_TITLE = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val P_TEST_TITLE = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val BORDER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 1f }
    private val LINE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 0.5f }

    /**
     * The main function to generate a PDF report.
     */
    fun generatePdf(
        context: Context,
        headerData: ReportHeaderData,
        testName: String,
        testStandard: String,
        drawContent: (canvas: Canvas, startY: Float) -> Unit
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Draw the standardized header
        val contentStartY = drawHeader(context, canvas, headerData, testName, testStandard)

        // 2. Draw the specific content for the test
        drawContent(canvas, contentStartY)

        doc.finishPage(page)
        saveFile(context, "${testName.replace(" ", "_")}_Report.pdf", doc)
        doc.close()
    }

    private fun drawHeader(context: Context, canvas: Canvas, data: ReportHeaderData, testName: String, testStandard: String): Float {
        val rightMargin = A4_WIDTH - MARGIN
        var y: Float

        // Draw Bilingual Text
        y = MARGIN + P_HEADER_EN.textSize
        canvas.drawText(data.line1_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line1_ar, rightMargin, y, P_HEADER_AR)
        y += 20
        canvas.drawText(data.line2_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line2_ar, rightMargin, y, P_HEADER_AR)
        y += 20
        canvas.drawText(data.line3_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line3_ar, rightMargin, y, P_HEADER_AR)
        y += 20
        canvas.drawText(data.line4_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line4_ar, rightMargin, y, P_HEADER_AR)
        y += 20
        canvas.drawText(data.line5_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line5_ar, rightMargin, y, P_HEADER_AR)
        y += 20
        canvas.drawText(data.line6_en, MARGIN, y, P_HEADER_EN); canvas.drawText(data.line6_ar, rightMargin, y, P_HEADER_AR)

        // --- Improved Logo Placement ---
        val logoCenterX = A4_WIDTH / 2f
        var logoY = MARGIN + 35f // Start Y position for the first logo
        val logoSize = 35f
        val logoSpacing = 5f // Space between logos

        val ministryLogo = uriToBitmap(context, data.ministryLogoUri.toUri())
        ministryLogo?.let {
            val logoRect = RectF(logoCenterX - logoSize / 2, logoY, logoCenterX + logoSize / 2, logoY + logoSize)
            canvas.drawBitmap(it, null, logoRect, null)
            logoY += logoSize + logoSpacing // Move down for the next logo
        }
        val consultantLogo = uriToBitmap(context, data.consultantLogoUri.toUri())
        consultantLogo?.let {
            val logoRect = RectF(logoCenterX - logoSize / 2, logoY, logoCenterX + logoSize / 2, logoY + logoSize)
            canvas.drawBitmap(it, null, logoRect, null)
            logoY += logoSize + logoSpacing
        }
        val contractorLogo = uriToBitmap(context, data.contractorLogoUri.toUri())
        contractorLogo?.let {
            val logoRect = RectF(logoCenterX - logoSize / 2, logoY, logoCenterX + logoSize / 2, logoY + logoSize)
            canvas.drawBitmap(it, null, logoRect, null)
        }

        // --- Continue layout after text block ---
        y = MARGIN + 125f // Set Y position after the text block

        // Divider Line
        canvas.drawLine(MARGIN, y, rightMargin, y, BORDER_PAINT)
        y += 25

        // Project Title
        canvas.drawText(data.projectTitle_ar, (A4_WIDTH / 2f), y, P_PROJECT_TITLE)
        y += 25

        // Test Title
        val fullTestTitle = "$testName ( $testStandard )"
        canvas.drawText(fullTestTitle, (A4_WIDTH / 2f), y, P_TEST_TITLE)

        // Underline for Test Title
        val textWidth = P_TEST_TITLE.measureText(fullTestTitle)
        val lineStartX = (A4_WIDTH / 2f) - (textWidth / 2)
        val lineEndX = (A4_WIDTH / 2f) + (textWidth / 2)
        canvas.drawLine(lineStartX, y + 3, lineEndX, y + 3, P_TEST_TITLE)
        y += 20

        return y // Return the starting Y position for the main content
    }

    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return if (uri.toString().isEmpty()) {
            null
        } else {
            try {
                // Ensure the content resolver has permission to read the URI
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                val fileDescriptor = parcelFileDescriptor?.fileDescriptor
                val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                parcelFileDescriptor?.close()
                image
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun saveFile(context: Context, fileName: String, doc: PdfDocument) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        else Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName))
        try {
            uri?.let { resolver.openOutputStream(it)?.use { s -> doc.writeTo(s) }; Toast.makeText(context, "Report saved to Downloads", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}

