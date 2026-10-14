package com.example.security

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility for generating and decrypting exclusive exam QR codes.
 *
 * Uses AES-256-GCM (Authenticated Encryption with Associated Data).
 * Other QR scanners (Google Lens, standard camera, generic barcode scanner)
 * only see an unreadable "EXAMSEC:..." token that cannot be opened as a URL.
 */
object SecureQrCrypto {

    private const val MASTER_KEY_SEED = "SMAN1_KEPANJEN_SECURE_EXAM_AES_256_KEY_2026_V1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    // Prefixes
    const val PREFIX_SECURE_NO_PIN = "EXAMSEC:v1:N:"
    const val PREFIX_SECURE_WITH_PIN = "EXAMSEC:v1:P:"
    const val PREFIX_GENERIC = "EXAMSEC:"

    /**
     * Checks if the scanned string is an exclusive encrypted QR code from this app.
     */
    fun isSecureExamQr(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith(PREFIX_GENERIC, ignoreCase = true)
    }

    /**
     * Checks if the encrypted QR code requires a proctor session PIN to decrypt.
     */
    fun requiresSessionPin(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith(PREFIX_WITH_PIN_CHECK, ignoreCase = true)
    }

    private val PREFIX_WITH_PIN_CHECK = PREFIX_SECURE_WITH_PIN

    /**
     * Derives an AES-256 key using SHA-256 hash of the master seed and optional session PIN.
     */
    private fun deriveKey(sessionPin: String?): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        val input = if (sessionPin.isNullOrBlank()) {
            MASTER_KEY_SEED
        } else {
            "$MASTER_KEY_SEED:SESSION_PIN:${sessionPin.trim()}"
        }
        val keyBytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext payload into an exclusive QR token format.
     *
     * @param plainJson The JSON string containing exam server configuration.
     * @param sessionPin Optional PIN set by teacher/proctor. If empty, uses app master key.
     * @return Encrypted token string (e.g. EXAMSEC:v1:N:...)
     */
    fun encryptPayload(plainJson: String, sessionPin: String? = null): String {
        return try {
            val key = deriveKey(sessionPin)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
            val cipherBytes = cipher.doFinal(plainBytes)

            // Pack: IV (12 bytes) + Ciphertext & Tag
            val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
            buffer.put(iv)
            buffer.put(cipherBytes)
            val combined = buffer.array()

            val encodedBase64 = Base64.encodeToString(
                combined,
                Base64.NO_WRAP or Base64.URL_SAFE
            )

            val prefix = if (sessionPin.isNullOrBlank()) PREFIX_SECURE_NO_PIN else PREFIX_SECURE_WITH_PIN
            prefix + encodedBase64
        } catch (e: Exception) {
            throw IllegalStateException("Gagal mengenkripsi payload QR: ${e.message}", e)
        }
    }

    /**
     * Decrypts an exclusive QR token string back to plaintext JSON.
     *
     * @param encryptedToken The scanned QR content starting with EXAMSEC:...
     * @param sessionPin Optional session PIN provided by user.
     * @return Decrypted JSON string, or null if decryption / authentication failed.
     */
    fun decryptPayload(encryptedToken: String, sessionPin: String? = null): String? {
        val trimmed = encryptedToken.trim()
        if (!isSecureExamQr(trimmed)) return null

        return try {
            val rawBase64 = when {
                trimmed.startsWith(PREFIX_SECURE_NO_PIN, ignoreCase = true) ->
                    trimmed.substring(PREFIX_SECURE_NO_PIN.length)
                trimmed.startsWith(PREFIX_SECURE_WITH_PIN, ignoreCase = true) ->
                    trimmed.substring(PREFIX_SECURE_WITH_PIN.length)
                trimmed.startsWith("EXAMSEC:v1:", ignoreCase = true) ->
                    trimmed.substring("EXAMSEC:v1:".length)
                else ->
                    trimmed.substring(PREFIX_GENERIC.length)
            }

            val combined = Base64.decode(rawBase64, Base64.NO_WRAP or Base64.URL_SAFE)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

            val key = deriveKey(sessionPin)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
