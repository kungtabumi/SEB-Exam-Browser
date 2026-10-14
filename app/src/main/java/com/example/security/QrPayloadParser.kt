package com.example.security

import android.net.Uri
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object QrPayloadParser {

    // Default secret key for HMAC signature verification of SMAN 1 Kepanjen Exam QRs
    private const val HMAC_KEY = "SMAN1_KEPANJEN_SECURE_EXAM_BROWSER_KEY_2026"

    private val URL_REGEX = Pattern.compile(
        """(https?://[^\s<>"'{}|\\^`]+|\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?(?:/[^\s<>"'{}|\\^`]*)?|[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}(?::\d+)?(?:/[^\s<>"'{}|\\^`]*)?)""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(qrText: String, sessionPin: String? = null): ParsedQrPayload {
        val trimmed = qrText.trim()
        if (trimmed.isEmpty()) {
            return ParsedQrPayload(
                rawText = qrText,
                type = "unknown",
                serverUrl = "",
                errorMessage = "QR Code kosong."
            )
        }

        // 1. Check Exclusive Encrypted QR Format (AES-256-GCM)
        if (SecureQrCrypto.isSecureExamQr(trimmed)) {
            val requiresPin = SecureQrCrypto.requiresSessionPin(trimmed)
            val decryptedJson = SecureQrCrypto.decryptPayload(trimmed, sessionPin)

            if (decryptedJson == null) {
                return if (requiresPin && sessionPin.isNullOrBlank()) {
                    ParsedQrPayload(
                        rawText = qrText,
                        type = "encrypted_needs_pin",
                        serverUrl = "",
                        isEncrypted = true,
                        encryptionType = "AES-256-GCM",
                        requiresSessionPin = true,
                        errorMessage = "QR Code terenkripsi membutuhkan PIN Sesi dari Pengawas."
                    )
                } else {
                    ParsedQrPayload(
                        rawText = qrText,
                        type = "encrypted_invalid",
                        serverUrl = "",
                        isEncrypted = true,
                        encryptionType = "AES-256-GCM",
                        requiresSessionPin = requiresPin,
                        errorMessage = "QR Code terenkripsi khusus aplikasi ini gagal didekripsi (PIN atau kunci salah, atau QR rusak)."
                    )
                }
            }

            // Successfully decrypted! Parse inner JSON
            val parsed = parseJsonPayload(decryptedJson, qrText, isEncrypted = true, encryptionType = "AES-256-GCM")
            if (parsed != null) {
                return parsed
            } else {
                return ParsedQrPayload(
                    rawText = qrText,
                    type = "encrypted_invalid",
                    serverUrl = "",
                    isEncrypted = true,
                    encryptionType = "AES-256-GCM",
                    errorMessage = "Konten dalam QR terenkripsi tidak dalam format yang valid."
                )
            }
        }

        // 2. Check Admin Unlock QR format
        if (trimmed.equals("admin_unlock", ignoreCase = true) ||
            trimmed.contains("\"type\":\"admin_unlock\"") ||
            trimmed.contains("\"type\": \"admin_unlock\"") ||
            trimmed.contains("unlock_exam")
        ) {
            return ParsedQrPayload(
                rawText = qrText,
                type = "admin_unlock",
                serverUrl = ""
            )
        }

        // 3. Try standard JSON parsing
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val parsed = parseJsonPayload(trimmed, qrText, isEncrypted = false, encryptionType = null)
            if (parsed != null) return parsed
        }

        // 4. SEB Protocol Scheme (seb:// or sebs://)
        if (trimmed.startsWith("seb://", ignoreCase = true)) {
            val converted = "http://" + trimmed.substring(6)
            return ParsedQrPayload(
                rawText = qrText,
                type = "seb_url",
                school = "SMAN 1 Kepanjen",
                serverUrl = cleanUrl(converted)
            )
        }
        if (trimmed.startsWith("sebs://", ignoreCase = true)) {
            val converted = "https://" + trimmed.substring(7)
            return ParsedQrPayload(
                rawText = qrText,
                type = "seb_url",
                school = "SMAN 1 Kepanjen",
                serverUrl = cleanUrl(converted)
            )
        }

        // 4. Extract any standard URL (http, https, ip:port, domain.com)
        val extractedUrl = extractUrlFromText(trimmed)
        if (extractedUrl != null) {
            return ParsedQrPayload(
                rawText = qrText,
                type = "plain_url",
                school = "SMAN 1 Kepanjen",
                serverUrl = cleanUrl(extractedUrl)
            )
        }

        // 5. Generic String / Token / Arbitrary QR content
        // If it looks like a domain or host (e.g. 192.168.1.1:8080 or cbt-server)
        val cleaned = cleanUrl(trimmed)
        return ParsedQrPayload(
            rawText = qrText,
            type = "generic_qr",
            school = "SMAN 1 Kepanjen",
            serverUrl = cleaned
        )
    }

    private fun parseJsonPayload(
        jsonString: String,
        rawText: String,
        isEncrypted: Boolean = false,
        encryptionType: String? = null
    ): ParsedQrPayload? {
        return try {
            val json = JSONObject(jsonString)
            val type = if (isEncrypted) "encrypted_exclusive" else json.optString("type", "universal_exam")
            val version = json.optInt("version", 1)
            val school = json.optString("school", json.optString("sekolah", "SMAN 1 Kepanjen"))

            val serverCandidate = json.optString("server", "")
                .ifEmpty { json.optString("url", "") }
                .ifEmpty { json.optString("link", "") }
                .ifEmpty { json.optString("serverUrl", "") }
                .ifEmpty { json.optString("server_url", "") }
                .ifEmpty { json.optString("cbtUrl", "") }
                .ifEmpty { json.optString("cbt_url", "") }
                .ifEmpty { json.optString("examUrl", "") }
                .ifEmpty { json.optString("exam_url", "") }
                .ifEmpty { json.optString("lmsUrl", "") }
                .ifEmpty { json.optString("lms_url", "") }
                .ifEmpty { json.optString("googleFormUrl", "") }
                .ifEmpty { json.optString("formUrl", "") }
                .ifEmpty { json.optString("moodleUrl", "") }
                .ifEmpty { json.optString("moodle_url", "") }
                .ifEmpty { json.optString("web", "") }
                .ifEmpty { json.optString("host", "") }
                .ifEmpty { json.optString("address", "") }

            val examCode = json.optString("examCode", "")
                .ifEmpty { json.optString("exam_code", "") }
                .ifEmpty { json.optString("code", "") }
                .ifEmpty { json.optString("token", "") }
                .ifEmpty { null }

            val examName = json.optString("examName", "")
                .ifEmpty { json.optString("exam_name", "") }
                .ifEmpty { json.optString("title", "") }
                .ifEmpty { json.optString("name", "") }
                .ifEmpty { json.optString("subject", "") }
                .ifEmpty { json.optString("mapel", "") }
                .ifEmpty { null }

            val issuedAt = if (json.has("issuedAt") && !json.isNull("issuedAt")) json.optString("issuedAt") else null
            val expiresAt = if (json.has("expiresAt") && !json.isNull("expiresAt")) json.optString("expiresAt") else null
            val signature = if (json.has("signature") && !json.isNull("signature")) json.optString("signature") else null

            var isExpired = false
            if (!expiresAt.isNullOrEmpty()) {
                isExpired = isDateExpired(expiresAt)
            }

            var isSignatureValid = true
            if (!signature.isNullOrEmpty()) {
                isSignatureValid = verifyHmacSignature(serverCandidate, examCode ?: "", expiresAt ?: "", signature)
            }

            val finalUrl = if (serverCandidate.isNotBlank()) {
                cleanUrl(serverCandidate)
            } else {
                extractUrlFromText(jsonString) ?: ""
            }

            val errorMessage = if (isExpired) "QR Code ujian sudah kedaluwarsa." else null

            ParsedQrPayload(
                rawText = rawText,
                type = type,
                version = version,
                school = school,
                serverUrl = finalUrl,
                examCode = examCode,
                examName = examName,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                signature = signature,
                isSignatureValid = isSignatureValid,
                isExpired = isExpired,
                isEncrypted = isEncrypted,
                encryptionType = encryptionType,
                requiresSessionPin = false,
                errorMessage = errorMessage
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractUrlFromText(text: String): String? {
        val matcher = URL_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun cleanUrl(url: String): String {
        var result = url.trim()
        // Remove surrounding quotes or brackets if present
        result = result.trim('"', '\'', '<', '>', '{', '}', '[', ']')

        // Handle SEB schemes
        if (result.startsWith("seb://", ignoreCase = true)) {
            result = "http://" + result.substring(6)
        } else if (result.startsWith("sebs://", ignoreCase = true)) {
            result = "https://" + result.substring(7)
        } else if (result.startsWith("http://", ignoreCase = true)) {
            result = "http://" + result.substring(7)
        } else if (result.startsWith("https://", ignoreCase = true)) {
            result = "https://" + result.substring(8)
        } else {
            // If user did not include scheme, check if it's a local IPv4 address, localhost, or port
            val isIpOrLocal = result.matches(
                Regex("""^(?:(?:192\.168\.|10\.|172\.(?:1[6-9]|2[0-9]|3[01])\.|127\.|localhost|\d{1,3}(?:\.\d{1,3}){3})|\S+:\d+).*""", RegexOption.IGNORE_CASE)
            )
            result = if (isIpOrLocal) {
                "http://$result"
            } else {
                "https://$result"
            }
        }
        return result.removeSuffix("/")
    }

    private fun isDateExpired(dateIsoStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expireDate = sdf.parse(dateIsoStr) ?: return false
            Date().after(expireDate)
        } catch (e: Exception) {
            false
        }
    }

    fun generateSignature(server: String, examCode: String, expiresAt: String): String {
        return try {
            val payload = "$server|$examCode|$expiresAt"
            val hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_KEY.toByteArray(), "HmacSHA256")
            hmac.init(secretKey)
            val bytes = hmac.doFinal(payload.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun verifyHmacSignature(server: String, examCode: String, expiresAt: String, signature: String): Boolean {
        val expected = generateSignature(server, examCode, expiresAt)
        return expected.equals(signature, ignoreCase = true)
    }
}
