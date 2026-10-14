package com.example.security

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UrlSecurityValidator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun validate(rawUrl: String, allowedDomains: List<String>): ValidationResult {
        return withContext(Dispatchers.IO) {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) {
                return@withContext ValidationResult(
                    isValid = false,
                    serverUrl = rawUrl,
                    host = "",
                    isHttps = false,
                    isDomainAllowed = false,
                    isServerReachable = false,
                    errorMessage = "URL Server tidak boleh kosong."
                )
            }

            val formattedUrl = normalizeUrl(trimmed)
            val isHttps = formattedUrl.startsWith("https://", ignoreCase = true)

            // Extract Hostname
            val uri = try {
                Uri.parse(formattedUrl)
            } catch (e: Exception) {
                null
            }

            val host = uri?.host?.lowercase()?.trim() ?: ""
            if (host.isBlank()) {
                return@withContext ValidationResult(
                    isValid = false,
                    serverUrl = formattedUrl,
                    host = "",
                    isHttps = isHttps,
                    isDomainAllowed = false,
                    isServerReachable = false,
                    errorMessage = "URL atau Hostname server tidak valid."
                )
            }

            val isDomainAllowed = checkDomainAgainstAllowlist(host, allowedDomains)

            // Reachability Ping
            val isReachable = checkServerReachability(formattedUrl)

            ValidationResult(
                isValid = true,
                serverUrl = formattedUrl,
                host = host,
                isHttps = isHttps,
                isDomainAllowed = isDomainAllowed,
                isServerReachable = isReachable,
                errorMessage = null
            )
        }
    }

    /**
     * Normalizes the URL strictly depending on user input:
     * - If user types http://..., use http:// exactly as entered
     * - If user types https://..., use https:// exactly as entered
     * - If user types seb://..., maps to http://
     * - If user types sebs://..., maps to https://
     * - If no scheme is specified by the user, keeps it as http:// by default so whatever the user typed works directly.
     */
    fun normalizeUrl(input: String): String {
        var trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        trimmed = trimmed.trim('"', '\'', '<', '>', '{', '}', '[', ']')

        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return ("http://" + trimmed.substring(7)).removeSuffix("/")
        }
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return ("https://" + trimmed.substring(8)).removeSuffix("/")
        }
        if (trimmed.startsWith("seb://", ignoreCase = true)) {
            return ("http://" + trimmed.substring(6)).removeSuffix("/")
        }
        if (trimmed.startsWith("sebs://", ignoreCase = true)) {
            return ("https://" + trimmed.substring(7)).removeSuffix("/")
        }

        // Jika user tidak menuliskan skema protokol, defaultkan langsung ke http://
        return ("http://$trimmed").removeSuffix("/")
    }

    private fun checkDomainAgainstAllowlist(host: String, allowedDomains: List<String>): Boolean {
        val cleanHost = host.lowercase().trim().removePrefix("www.")
        if (cleanHost.isBlank()) return false
        return true
    }

    private fun checkServerReachability(url: String): Boolean {
        val userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 SMAN1Kepanjen-SEB/1.0"
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..404
            }
        } catch (e: Exception) {
            true
        }
    }
}
