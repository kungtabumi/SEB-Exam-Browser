package com.example

import com.example.data.local.SavedQrEntity
import com.example.qr.QrGenerator
import com.example.qr.QrPrintData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrPdfPrinterTest {

    @Test
    fun testGenerateRawQrBitmap() {
        val bitmap = QrGenerator.generateRawBitmap("EXAMSEC:TEST_PAYLOAD", 256)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width == 256)
        assertTrue(bitmap.height == 256)
    }

    @Test
    fun testQrPrintDataFromSavedQr() {
        val entity = SavedQrEntity(
            id = 1L,
            examName = "Matematika Asesmen",
            examCode = "MTK-2026",
            serverUrl = "https://cbt.sekolah.sch.id",
            schoolName = "SMAN 1 Kepanjen",
            qrPayload = "https://cbt.sekolah.sch.id/moodle",
            isEncrypted = true,
            sessionPin = "123456",
            expiresAt = 1790000000000L
        )

        val printData = QrPrintData.fromSavedQr(entity)
        assertEquals("Matematika Asesmen", printData.examName)
        assertEquals("MTK-2026", printData.examCode)
        assertEquals("https://cbt.sekolah.sch.id", printData.serverUrl)
        assertEquals("SMAN 1 Kepanjen", printData.schoolName)
        assertEquals("https://cbt.sekolah.sch.id/moodle", printData.qrPayload)
        assertTrue(printData.isEncrypted)
        assertEquals("123456", printData.sessionPin)
        assertEquals(1790000000000L, printData.expiresAt)
    }
}
