package com.example

import com.example.security.QrPayloadParser
import com.example.security.SecureQrCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureQrCryptoTest {

    @Test
    fun testEncryptAndDecrypt_WithoutSessionPin() {
        val payload = """{"type":"moodle_exam","server":"https://cbt.sekolah.sch.id","examCode":"MAT-01","school":"SMAN 1 Kepanjen"}"""
        val encryptedToken = SecureQrCrypto.encryptPayload(payload)

        // Must start with EXAMSEC: prefix
        assertTrue(encryptedToken.startsWith("EXAMSEC:v1:N:"))
        assertTrue(SecureQrCrypto.isSecureExamQr(encryptedToken))
        assertFalse(SecureQrCrypto.requiresSessionPin(encryptedToken))

        // Normal scanner cannot open as URL
        assertFalse(encryptedToken.startsWith("http://"))
        assertFalse(encryptedToken.startsWith("https://"))

        // Decryption in app
        val decrypted = SecureQrCrypto.decryptPayload(encryptedToken)
        assertNotNull(decrypted)
        assertEquals(payload, decrypted)

        // Parsing in QrPayloadParser
        val parsed = QrPayloadParser.parse(encryptedToken)
        assertEquals("https://cbt.sekolah.sch.id", parsed.serverUrl)
        assertEquals("MAT-01", parsed.examCode)
        assertEquals("SMAN 1 Kepanjen", parsed.school)
        assertTrue(parsed.isEncrypted)
        assertEquals("AES-256-GCM", parsed.encryptionType)
    }

    @Test
    fun testEncryptAndDecrypt_WithSessionPin() {
        val payload = """{"type":"moodle_exam","server":"https://ujian.sekolah.sch.id","examCode":"BIO-02","school":"SMAN 1 Kepanjen"}"""
        val pin = "9988"
        val encryptedToken = SecureQrCrypto.encryptPayload(payload, sessionPin = pin)

        assertTrue(encryptedToken.startsWith("EXAMSEC:v1:P:"))
        assertTrue(SecureQrCrypto.requiresSessionPin(encryptedToken))

        // Decryption without PIN or wrong PIN must fail
        val decryptedWithoutPin = SecureQrCrypto.decryptPayload(encryptedToken, sessionPin = null)
        assertNull(decryptedWithoutPin)

        val decryptedWrongPin = SecureQrCrypto.decryptPayload(encryptedToken, sessionPin = "1111")
        assertNull(decryptedWrongPin)

        // Decryption with correct PIN succeeds
        val decryptedCorrectPin = SecureQrCrypto.decryptPayload(encryptedToken, sessionPin = pin)
        assertNotNull(decryptedCorrectPin)
        assertEquals(payload, decryptedCorrectPin)

        // QrPayloadParser requesting pin
        val parsedWithoutPin = QrPayloadParser.parse(encryptedToken)
        assertEquals("encrypted_needs_pin", parsedWithoutPin.type)
        assertTrue(parsedWithoutPin.requiresSessionPin)

        // QrPayloadParser with correct pin
        val parsedWithPin = QrPayloadParser.parse(encryptedToken, sessionPin = pin)
        assertEquals("https://ujian.sekolah.sch.id", parsedWithPin.serverUrl)
        assertEquals("BIO-02", parsedWithPin.examCode)
        assertTrue(parsedWithPin.isEncrypted)
    }

    @Test
    fun testExternalScanners_CannotReadOrOpenEncryptedPayload() {
        val payload = """{"server":"https://server.internal/cbt"}"""
        val encryptedToken = SecureQrCrypto.encryptPayload(payload)

        // To any browser or standard QR reader, it is not a URL
        assertFalse(encryptedToken.contains("https://server.internal/cbt"))
        assertTrue(encryptedToken.startsWith("EXAMSEC:"))
    }
}
