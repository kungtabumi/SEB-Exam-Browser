package com.example.security

data class ParsedQrPayload(
    val rawText: String,
    val type: String, // "universal_exam", "exam_server", "cbt_exam", "moodle_exam_server", "moodle_exam", "admin_unlock", "admin_config", "plain_url", "encrypted_exclusive"
    val version: Int = 1,
    val school: String = "SMAN 1 Kepanjen",
    val serverUrl: String,
    val examCode: String? = null,
    val examName: String? = null,
    val issuedAt: String? = null,
    val expiresAt: String? = null,
    val signature: String? = null,
    val isSignatureValid: Boolean = true,
    val isExpired: Boolean = false,
    val isEncrypted: Boolean = false,
    val encryptionType: String? = null,
    val requiresSessionPin: Boolean = false,
    val errorMessage: String? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val serverUrl: String,
    val host: String,
    val isHttps: Boolean,
    val isDomainAllowed: Boolean,
    val isServerReachable: Boolean,
    val errorMessage: String? = null
) {
    val protocol: String get() = if (isHttps) "HTTPS" else "HTTP"
}
