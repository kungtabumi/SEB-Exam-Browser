package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Penyimpanan persisten untuk PIN Administrator / Pengawas.
 * Menjamin perubahan PIN dari menu admin tersimpan permanen dan tidak hilang
 * saat sesi ujian berakhir atau aplikasi ditutup.
 */
class AdminPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("admin_security_prefs", Context.MODE_PRIVATE)

    private val _adminPin = MutableStateFlow(loadAdminPin())
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    private val _exitPin = MutableStateFlow(loadExitPin())
    val exitPin: StateFlow<String> = _exitPin.asStateFlow()

    private fun loadAdminPin(): String {
        return prefs.getString(KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN) ?: DEFAULT_ADMIN_PIN
    }

    private fun loadExitPin(): String {
        return prefs.getString(KEY_EXIT_PIN, DEFAULT_EXIT_PIN) ?: DEFAULT_EXIT_PIN
    }

    fun getAdminPin(): String {
        return prefs.getString(KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN) ?: DEFAULT_ADMIN_PIN
    }

    fun getExitPin(): String {
        return prefs.getString(KEY_EXIT_PIN, DEFAULT_EXIT_PIN) ?: DEFAULT_EXIT_PIN
    }

    fun setAdminPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.length >= 4) {
            prefs.edit().putString(KEY_ADMIN_PIN, trimmed).apply()
            _adminPin.value = trimmed
        }
    }

    fun setExitPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.length >= 4) {
            prefs.edit().putString(KEY_EXIT_PIN, trimmed).apply()
            _exitPin.value = trimmed
        }
    }

    companion object {
        const val DEFAULT_ADMIN_PIN = "1ctAdm1n!"
        const val DEFAULT_EXIT_PIN = "R4hasi@"
        private const val KEY_ADMIN_PIN = "key_admin_pin"
        private const val KEY_EXIT_PIN = "key_exit_pin"

        @Volatile
        private var instance: AdminPreferences? = null

        fun getInstance(context: Context): AdminPreferences {
            return instance ?: synchronized(this) {
                instance ?: AdminPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
