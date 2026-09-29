package com.verimark.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.verimark.app.data.CaseEntity
import com.verimark.app.data.MarkerEntity
import com.verimark.app.util.formatMs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_WIDTH = 595   // A4 width (points)
private const val PAGE_HEIGHT = 842  // A4 height (points)
private const val MARGIN = 40f
private const val LINE_HEIGHT = 20f

private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = 20f
    typeface = Typeface.DEFAULT_BOLD
}

private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = 12f
}

private val dividerPaint = Paint().apply {
    color = Color.GRAY
    strokeWidth = 1f
}

/**
 * Generates a local PDF report using the native [PdfDocument] Canvas API.
 * No external PDF libraries and no network access.
 */
fun generatePdfReport(context: Context, case: CaseEntity, markers: List<MarkerEntity>): File {
    val document = PdfDocument()
    var page = newPage(document)
    var canvas = page.canvas
    var y = drawHeader(canvas, case, markers.size)

    markers.forEachIndexed { index, marker ->
        if (y > PAGE_HEIGHT - MARGIN - LINE_HEIGHT) {
            document.finishPage(page)
            page = newPage(document)
            canvas = page.canvas
            y = MARGIN + LINE_HEIGHT
        }
        val line = "${index + 1}.  ${formatMs(marker.positionMs)}  -  ${marker.label}"
        canvas.drawText(line, MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
    }

    document.finishPage(page)

    val file = File(context.cacheDir, "VeriMark_Report_${System.currentTimeMillis()}.pdf")
    file.outputStream().use { stream ->
        document.writeTo(stream)
    }
    document.close()
    return file
}

private fun newPage(document: PdfDocument): PdfDocument.Page =
    document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())

private fun drawHeader(canvas: Canvas, case: CaseEntity, markerCount: Int): Float {
    canvas.drawText("VeriMark - Incident Report", MARGIN, MARGIN + 8f, titlePaint)
    canvas.drawText("Case: ${case.title}", MARGIN, MARGIN + 30f, bodyPaint)
    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(case.date))
    canvas.drawText("Date: $formattedDate", MARGIN, MARGIN + 50f, bodyPaint)
    canvas.drawText("Total markers: $markerCount", MARGIN, MARGIN + 70f, bodyPaint)
    canvas.drawLine(MARGIN, MARGIN + 80f, PAGE_WIDTH - MARGIN, MARGIN + 80f, dividerPaint)
    return MARGIN + 100f
}

/** Shares the generated PDF via a FileProvider-backed ACTION_SEND intent. */
fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Export PDF Report")
    context.startActivity(chooser)
}
