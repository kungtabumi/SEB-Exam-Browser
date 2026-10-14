package com.example.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.SavedQrEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QrPrintData(
    val examName: String,
    val examCode: String,
    val serverUrl: String,
    val schoolName: String,
    val qrPayload: String,
    val isEncrypted: Boolean,
    val sessionPin: String?,
    val expiresAt: Long
) {
    companion object {
        fun fromSavedQr(entity: SavedQrEntity): QrPrintData {
            return QrPrintData(
                examName = entity.examName,
                examCode = entity.examCode,
                serverUrl = entity.serverUrl,
                schoolName = entity.schoolName,
                qrPayload = entity.qrPayload,
                isEncrypted = entity.isEncrypted,
                sessionPin = entity.sessionPin,
                expiresAt = entity.expiresAt
            )
        }
    }
}

object QrPdfPrinter {

    /**
     * Membuka dialog cetak bawaan Android (Android Print Spooler).
     * Pengawas dapat langsung mencetak ke printer fisik atau memilih 'Simpan sebagai PDF'.
     */
    fun printQrCode(context: Context, data: QrPrintData) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Layanan cetak tidak tersedia pada perangkat ini", Toast.LENGTH_SHORT).show()
                return
            }

            val jobName = "QR_Ujian_${data.examCode.ifBlank { "CBT" }}"
            val printAdapter = QrPrintDocumentAdapter(context, data)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()

            printManager.print(jobName, printAdapter, printAttributes)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka menu cetak: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Menghasilkan file PDF ke direktori cache dan membagikannya via Android Intent Chooser
     * (misalnya ke WhatsApp, Telegram, Google Drive, Email, Bluetooth, dll).
     */
    fun shareQrPdf(context: Context, data: QrPrintData) {
        try {
            val pdfFile = generatePdfFile(context, data)
            if (pdfFile == null || !pdfFile.exists()) {
                Toast.makeText(context, "Gagal membuat dokumen PDF", Toast.LENGTH_SHORT).show()
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "QR Code Ujian - ${data.examName} (${data.examCode})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Berikut adalah lembar QR Code Ujian '${data.examName}' (Kode: ${data.examCode}) untuk discan oleh siswa melalui aplikasi Exam Browser."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan PDF QR Code Ujian")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Membuat file PDF lokal di cache.
     */
    fun generatePdfFile(context: Context, data: QrPrintData): File? {
        return try {
            val cacheDir = File(context.cacheDir, "pdf_exports")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val sanitizedCode = data.examCode.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "EXAM" }
            val file = File(cacheDir, "QR_Ujian_${sanitizedCode}.pdf")

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait in points (72 DPI)
            val page = pdfDocument.startPage(pageInfo)

            drawQrDocumentPage(context, page.canvas, pageInfo.pageWidth, pageInfo.pageHeight, data)
            pdfDocument.finishPage(page)

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Menggambar seluruh tata letak halaman dokumen PDF A4 (595 x 842 pt).
     */
    internal fun drawQrDocumentPage(
        context: Context,
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        data: QrPrintData
    ) {
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        val nowFormatted = sdf.format(Date())
        val expireFormatted = sdf.format(Date(data.expiresAt))

        // 1. Background dasar putih bersih
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // 2. Header Banner Atas
        val headerHeight = 76f
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Deep Slate Navy
            isAntiAlias = true
        }
        val headerRect = RectF(margin, margin, pageWidth - margin, margin + headerHeight)
        canvas.drawRoundRect(headerRect, 10f, 10f, headerPaint)

        // Aksen Garis Biru Modern di bawah header
        val accentLinePaint = Paint().apply {
            color = Color.parseColor("#2563EB") // Primary Blue
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawLine(margin, margin + headerHeight, pageWidth - margin, margin + headerHeight, accentLinePaint)

        // Teks Header
        val headerTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("LEMBAR QR CODE UJIAN CBT", margin + 16f, margin + 28f, headerTitlePaint)

        val headerSubPaint = Paint().apply {
            color = Color.parseColor("#94A3B8") // Slate 400
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val schoolDisplay = if (data.schoolName.isNotBlank()) data.schoolName else "SISTEM ASESMEN TERKUNCI"
        canvas.drawText(schoolDisplay.uppercase(Locale.getDefault()), margin + 16f, margin + 46f, headerSubPaint)

        val headerSecurityBadge = Paint().apply {
            color = Color.parseColor("#38BDF8") // Sky Blue
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("EXAM BROWSER SECURE", pageWidth - margin - 16f, margin + 38f, headerSecurityBadge)

        var currentY = margin + headerHeight + 16f

        // 3. Kartu Informasi Ujian (Detail Box)
        val infoBoxHeight = 112f
        val infoBoxRect = RectF(margin, currentY, pageWidth - margin, currentY + infoBoxHeight)
        val infoBoxPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC") // Slate 50
            isAntiAlias = true
        }
        val infoBorderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0") // Slate 200
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(infoBoxRect, 10f, 10f, infoBoxPaint)
        canvas.drawRoundRect(infoBoxRect, 10f, 10f, infoBorderPaint)

        // Label & Isi Informasi
        val labelPaint = Paint().apply {
            color = Color.parseColor("#64748B") // Slate 500
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val valueBoldPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Slate 900
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valueNormalPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val col1X = margin + 14f
        val col2X = margin + (contentWidth / 2f) + 10f

        // Baris 1: Mata Pelajaran & Kode Ujian
        canvas.drawText("Mata Pelajaran / Asesmen:", col1X, currentY + 18f, labelPaint)
        canvas.drawText(data.examName.ifBlank { "Ujian Sekolah" }, col1X, currentY + 33f, valueBoldPaint)

        canvas.drawText("Kode Ujian:", col2X, currentY + 18f, labelPaint)
        canvas.drawText(data.examCode.ifBlank { "EXAM-001" }, col2X, currentY + 33f, valueBoldPaint)

        // Baris 2: Enkripsi Keamanan & PIN Sesi
        canvas.drawText("Keamanan Payload:", col1X, currentY + 54f, labelPaint)
        val secText = if (data.isEncrypted) "AES-256-GCM Terenkripsi (Hanya via Aplikasi)" else "Standar Terbuka"
        canvas.drawText(secText, col1X, currentY + 68f, valueNormalPaint)

        canvas.drawText("PIN Sesi:", col2X, currentY + 54f, labelPaint)
        val pinText = if (!data.sessionPin.isNullOrBlank()) data.sessionPin else "Tidak Memerlukan PIN"
        canvas.drawText(pinText, col2X, currentY + 68f, valueNormalPaint)

        // Baris 3: Masa Berlaku & URL Server
        canvas.drawText("Masa Berlaku s.d.:", col1X, currentY + 88f, labelPaint)
        canvas.drawText(expireFormatted, col1X, currentY + 101f, valueNormalPaint)

        canvas.drawText("Server Ujian:", col2X, currentY + 88f, labelPaint)
        val urlDisplay = if (data.serverUrl.length > 35) data.serverUrl.take(32) + "..." else data.serverUrl
        canvas.drawText(urlDisplay, col2X, currentY + 101f, valueNormalPaint)

        currentY += infoBoxHeight + 16f

        // 4. Area QR Code Utama
        val qrBoxHeight = 310f
        val qrBoxRect = RectF(margin, currentY, pageWidth - margin, currentY + qrBoxHeight)
        canvas.drawRoundRect(qrBoxRect, 12f, 12f, infoBoxPaint)
        canvas.drawRoundRect(qrBoxRect, 12f, 12f, infoBorderPaint)

        // Generate QR Raw Bitmap
        val qrSizePx = 600
        val qrBitmap: Bitmap? = QrGenerator.generateRawBitmap(data.qrPayload, qrSizePx)

        val qrDisplaySize = 210f
        val qrLeft = margin + ((contentWidth - qrDisplaySize) / 2f)
        val qrTop = currentY + 20f

        if (qrBitmap != null) {
            // White background pad behind QR code
            val qrPadRect = RectF(qrLeft - 8f, qrTop - 8f, qrLeft + qrDisplaySize + 8f, qrTop + qrDisplaySize + 8f)
            val whitePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
            val borderQrPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(qrPadRect, 8f, 8f, whitePaint)
            canvas.drawRoundRect(qrPadRect, 8f, 8f, borderQrPaint)

            val destRect = RectF(qrLeft, qrTop, qrLeft + qrDisplaySize, qrTop + qrDisplaySize)
            val bmpPaint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }
            canvas.drawBitmap(qrBitmap, null, destRect, bmpPaint)
        }

        // Teks instruksi di bawah QR
        val qrPromptPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val qrSubPromptPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val centerX = pageWidth / 2f
        canvas.drawText(
            "PINDAI QR DI ATAS DENGAN APLIKASI EXAM BROWSER",
            centerX,
            qrTop + qrDisplaySize + 32f,
            qrPromptPaint
        )
        canvas.drawText(
            "Kamera biasa / browser umum tidak dapat membuka link soal",
            centerX,
            qrTop + qrDisplaySize + 48f,
            qrSubPromptPaint
        )

        currentY += qrBoxHeight + 14f

        // 5. Petunjuk Siswa & Aturan Pengerjaan
        val guideBoxHeight = 160f
        val guideBoxRect = RectF(margin, currentY, pageWidth - margin, currentY + guideBoxHeight)
        canvas.drawRoundRect(guideBoxRect, 10f, 10f, infoBoxPaint)
        canvas.drawRoundRect(guideBoxRect, 10f, 10f, infoBorderPaint)

        val guideHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PETUNJUK PENGERJAAN & ATURAN CBT:", margin + 14f, currentY + 22f, guideHeaderPaint)

        val guideItemPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val steps = listOf(
            "1. Buka aplikasi Exam Browser pada smartphone peserta.",
            "2. Tekan tombol 'Pindai QR Code' pada halaman utama.",
            "3. Arahkan kamera ke lembar QR Code di atas. Ujian otomatis terbuka dalam Kiosk Mode.",
            "4. Jika diminta PIN Sesi, masukkan PIN yang tertera pada lembar ini.",
            "5. Dilarang menekan tombol Beranda, Recent Apps, Split Screen, atau berpindah aplikasi.",
            "6. Pelanggaran sistem akan mencatat indikasi kecurangan dan mengunci aplikasi siswa."
        )

        var stepY = currentY + 40f
        for (step in steps) {
            canvas.drawText(step, margin + 14f, stepY, guideItemPaint)
            stepY += 19f
        }

        // 6. Footer Bawah
        val footerY = pageHeight - margin - 8f
        val footerLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(margin, footerY - 14f, pageWidth - margin, footerY - 14f, footerLinePaint)

        val footerTextPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dicetak pada: $nowFormatted WIB", margin, footerY, footerTextPaint)

        val footerRightPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Dokumen Resmi Asesmen CBT • Bersifat Rahasia", pageWidth - margin, footerY, footerRightPaint)
    }
}

/**
 * Adapter untuk integrasi dengan Android PrintManager.
 */
class QrPrintDocumentAdapter(
    private val context: Context,
    private val data: QrPrintData
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder("QR_Ujian_${data.examCode.ifBlank { "CBT" }}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val pdfDocument = PdfDocument()
        try {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            QrPdfPrinter.drawQrDocumentPage(
                context = context,
                canvas = page.canvas,
                pageWidth = pageInfo.pageWidth,
                pageHeight = pageInfo.pageHeight,
                data = data
            )
            pdfDocument.finishPage(page)

            FileOutputStream(destination.fileDescriptor).use { output ->
                pdfDocument.writeTo(output)
            }

            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
    }
}
