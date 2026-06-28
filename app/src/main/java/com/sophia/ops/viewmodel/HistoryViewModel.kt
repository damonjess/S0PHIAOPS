package com.sophia.ops.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.ScanSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.FileOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SophiaDatabase.getInstance(application)

    private val scanSessionDao = db.scanSessionDao()

    val sessions: StateFlow<List<ScanSession>> = scanSessionDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun exportCsv(context: Context) {
        if (sessions.value.isEmpty()) {
            Log.d(
                "CSV_EXPORT",
                "No scan history to export."
            )
            return
        }
        val sessionList = sessions.value

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val csvHeader = "Date,WiFi,Bluetooth,Threat\n"
        val csvData = sessionList.joinToString("\n") { 
            "${sdf.format(Date(it.timestamp))},${it.wifiCount},${it.bluetoothCount},${it.threatScore}" 
        }
        val csvContent = csvHeader + csvData
        
        val file = File(
            context.cacheDir,
            "SophiaOps_History.csv"
        )
        file.writeText(csvContent)

        shareFile(context, file, "text/csv")
    }

    fun generatePdfReport(context: Context) {
        val sessionList = sessions.value
        if (sessionList.isEmpty()) return

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        var y = 50f
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("S0PHIA OPS - Tactical Report", 50f, y, paint)
        
        y += 40f
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Generated on: ${sdf.format(Date())}", 50f, y, paint)

        y += 50f
        paint.isFakeBoldText = true
        canvas.drawText("Recent Scan History:", 50f, y, paint)
        
        y += 30f
        paint.isFakeBoldText = false
        paint.textSize = 12f
        canvas.drawText("Date", 50f, y, paint)
        canvas.drawText("WiFi", 200f, y, paint)
        canvas.drawText("BT", 300f, y, paint)
        canvas.drawText("Threat", 400f, y, paint)

        sessionList.take(20).forEach { session ->
            y += 25f
            if (y > 800) return@forEach
            canvas.drawText(sdf.format(Date(session.timestamp)), 50f, y, paint)
            canvas.drawText(session.wifiCount.toString(), 200f, y, paint)
            canvas.drawText(session.bluetoothCount.toString(), 300f, y, paint)
            canvas.drawText(session.threatScore.toString(), 400f, y, paint)
        }

        pdfDocument.finishPage(page)

        val fileName = "sophia_ops_report_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            shareFile(context, file, "application/pdf")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String
    ) {
        try {

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {

                type = mimeType

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Share Report"
                )
            )

        } catch (e: Exception) {

            Log.e(
                "CSV_EXPORT",
                "Failed to export CSV",
                e
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            scanSessionDao.deleteAllSessions()
        }
    }
}
