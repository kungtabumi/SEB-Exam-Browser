package com.example.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {

    fun generateRawBitmap(
        content: String,
        sizePx: Int = 512,
        fgArgb: Int = android.graphics.Color.BLACK,
        bgArgb: Int = android.graphics.Color.WHITE
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) fgArgb else bgArgb)
                }
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Color = Color.Black,
        backgroundColor: Color = Color.White
    ): ImageBitmap? {
        return generateRawBitmap(
            content = content,
            sizePx = sizePx,
            fgArgb = foregroundColor.toArgb(),
            bgArgb = backgroundColor.toArgb()
        )?.asImageBitmap()
    }
}
