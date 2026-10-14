package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdminPreferences
import com.example.data.local.AllowedDomainEntity
import com.example.data.local.AppDatabase
import com.example.data.local.SavedQrEntity
import com.example.data.local.ServerConfigEntity
import com.example.data.local.ServerHistoryEntity
import com.example.security.ParsedQrPayload
import com.example.security.QrPayloadParser
import com.example.security.UrlSecurityValidator
import com.example.security.ValidationResult
import com.example.ui.dialogs.AdminAuthMode
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenState {
    INITIAL_SETUP,
    QR_SCANNER,
    MOODLE_WEBVIEW,
    ADMIN_TOOLS
}

class ExamBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val configDao = db.serverConfigDao()
    private val domainDao = db.allowedDomainDao()
    private val historyDao = db.serverHistoryDao()
    private val savedQrDao = db.savedQrDao()
    private val themePreferences = ThemePreferences.getInstance(application)
    private val adminPreferences = AdminPreferences.getInstance(application)

    val adminPinFlow: StateFlow<String> = adminPreferences.adminPin

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    val serverConfig: StateFlow<ServerConfigEntity?> = configDao.getServerConfigFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allowedDomains: StateFlow<List<AllowedDomainEntity>> = domainDao.getAllowedDomainsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverHistory: StateFlow<List<ServerHistoryEntity>> = historyDao.getHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ticker untuk memperbarui filter waktu aktif setiap 15 detik atau saat dipicu manual
    private val _refreshTicker = MutableStateFlow(System.currentTimeMillis())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeSavedQrs: StateFlow<List<SavedQrEntity>> = _refreshTicker
        .flatMapLatest { now ->
            savedQrDao.getActiveSavedQrsFlow(now)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(ScreenState.INITIAL_SETUP)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    private val _parsedPayload = MutableStateFlow<ParsedQrPayload?>(null)
    val parsedPayload: StateFlow<ParsedQrPayload?> = _parsedPayload.asStateFlow()

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showAdminUnlockDialog = MutableStateFlow(false)
    val showAdminUnlockDialog: StateFlow<Boolean> = _showAdminUnlockDialog.asStateFlow()

    private val _adminAuthMode = MutableStateFlow(AdminAuthMode.EXIT_EXAM)
    val adminAuthMode: StateFlow<AdminAuthMode> = _adminAuthMode.asStateFlow()

    private val _isScreenLockedByBreach = MutableStateFlow(false)
    val isScreenLockedByBreach: StateFlow<Boolean> = _isScreenLockedByBreach.asStateFlow()

    private val _pendingAdminDestination = MutableStateFlow<ScreenState?>(null)
    val pendingAdminDestination: StateFlow<ScreenState?> = _pendingAdminDestination.asStateFlow()

    private val _pendingPinQrCode = MutableStateFlow<String?>(null)
    val pendingPinQrCode: StateFlow<String?> = _pendingPinQrCode.asStateFlow()

    init {
        viewModelScope.launch {
            domainDao.deleteDefaultDomains()
        }
        viewModelScope.launch {
            configDao.getServerConfigFlow().collect { config ->
                if (config != null && config.isExamActive) {
                    _currentScreen.value = ScreenState.MOODLE_WEBVIEW
                }
            }
        }
        // Background loop: membersihkan QR yang kedaluwarsa dan memperbarui filter daftar QR aktif
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                savedQrDao.deleteExpiredQrs(now)
                _refreshTicker.value = now
                delay(15_000L) // Cek setiap 15 detik
            }
        }
    }

    fun navigateTo(screen: ScreenState) {
        val currentConfig = serverConfig.value
        if (currentConfig?.isExamActive == true && screen != ScreenState.MOODLE_WEBVIEW) {
            _adminAuthMode.value = AdminAuthMode.EXIT_EXAM
            _pendingAdminDestination.value = screen
            _showAdminUnlockDialog.value = true
            return
        }
        if (screen == ScreenState.ADMIN_TOOLS) {
            _adminAuthMode.value = AdminAuthMode.ADMIN_MENU
            _pendingAdminDestination.value = ScreenState.ADMIN_TOOLS
            _showAdminUnlockDialog.value = true
            return
        }
        _currentScreen.value = screen
    }

    fun openQrScanner() {
        _errorMessage.value = null
        navigateTo(ScreenState.QR_SCANNER)
    }

    fun validateAndPreviewUrl(rawUrl: String) {
        viewModelScope.launch {
            _isValidating.value = true
            _errorMessage.value = null

            val payload = QrPayloadParser.parse(rawUrl)
            _parsedPayload.value = payload

            if (payload.type == "encrypted_needs_pin" || (payload.requiresSessionPin && payload.errorMessage != null)) {
                _isValidating.value = false
                _pendingPinQrCode.value = rawUrl
                return@launch
            }

            if (payload.errorMessage != null) {
                _errorMessage.value = payload.errorMessage
                _isValidating.value = false
                return@launch
            }

            val domainsList = domainDao.getAllowedDomainsList()
            val valResult = UrlSecurityValidator.validate(payload.serverUrl, domainsList)
            _validationResult.value = valResult
            _isValidating.value = false

            if (valResult.isValid) {
                _errorMessage.value = null
                connectServerDirectly(valResult, payload)
            } else {
                _errorMessage.value = valResult.errorMessage ?: "Server ujian tidak valid."
            }
        }
    }

    fun submitSessionPinForQr(pin: String) {
        val qr = _pendingPinQrCode.value ?: return
        _pendingPinQrCode.value = null
        processScannedQr(qr, pin)
    }

    fun cancelSessionPinPrompt() {
        _pendingPinQrCode.value = null
        _errorMessage.value = "Pemindaian dibatalkan: PIN sesi tidak dimasukkan."
        _currentScreen.value = ScreenState.INITIAL_SETUP
    }

    fun processScannedQr(qrText: String, sessionPin: String? = null) {
        viewModelScope.launch {
            _isValidating.value = true
            _errorMessage.value = null

            val payload = QrPayloadParser.parse(qrText, sessionPin)
            _parsedPayload.value = payload

            // Check if encrypted QR needs session pin from proctor
            if (payload.type == "encrypted_needs_pin" || (payload.requiresSessionPin && sessionPin.isNullOrBlank())) {
                _isValidating.value = false
                _pendingPinQrCode.value = qrText
                return@launch
            }

            // Check Admin Unlock QR
            if (payload.type == "admin_unlock") {
                val target = _pendingAdminDestination.value ?: ScreenState.INITIAL_SETUP
                _pendingAdminDestination.value = null
                _showAdminUnlockDialog.value = false
                _isValidating.value = false

                configDao.deleteServerConfig()
                historyDao.clearHistory()
                _validationResult.value = null
                _parsedPayload.value = null
                _currentScreen.value = target
                return@launch
            }

            // Jika sedang dalam alur scan QR buka kunci ujian tetapi QR yang discan bukan QR buka kunci admin
            if (_pendingAdminDestination.value != null) {
                _isValidating.value = false
                _errorMessage.value = "QR Code yang dipindai bukan QR Buka Kunci Admin!"
                _currentScreen.value = ScreenState.MOODLE_WEBVIEW
                return@launch
            }

            if (payload.errorMessage != null) {
                _errorMessage.value = payload.errorMessage
                _isValidating.value = false
                _currentScreen.value = ScreenState.INITIAL_SETUP
                return@launch
            }

            val domainsList = domainDao.getAllowedDomainsList()
            val valResult = UrlSecurityValidator.validate(payload.serverUrl, domainsList)
            _validationResult.value = valResult
            _isValidating.value = false

            if (valResult.isValid) {
                // Langsung masuk ke ujian tanpa menampilkan menu konfirmasi agar URL tidak diketahui siswa
                connectServerDirectly(valResult, payload)
            } else {
                _errorMessage.value = valResult.errorMessage ?: "Server ujian tidak valid."
                _currentScreen.value = ScreenState.INITIAL_SETUP
            }
        }
    }

    /**
     * Menyimpan konfigurasi dan langsung membuka tampilan WebView ujian secara aman.
     */
    private suspend fun connectServerDirectly(valResult: ValidationResult, payload: ParsedQrPayload?) {
        val persistentPin = adminPreferences.getAdminPin()
        val config = ServerConfigEntity(
            id = 1,
            serverUrl = valResult.serverUrl,
            schoolName = payload?.school ?: "SMAN 1 Kepanjen",
            examCode = payload?.examCode,
            examName = payload?.examName,
            adminPin = persistentPin,
            isExamActive = true, // Lock Exam Active
            lastUpdated = System.currentTimeMillis()
        )

        configDao.saveServerConfig(config)

        // Auto-add server domain to allowed domains list
        try {
            val host = android.net.Uri.parse(valResult.serverUrl).host?.lowercase()?.trim()
            if (!host.isNullOrBlank()) {
                domainDao.insertDomain(AllowedDomainEntity(domain = host, isDefault = false))
            }
        } catch (_: Exception) {}

        historyDao.insertHistory(
            ServerHistoryEntity(
                serverUrl = valResult.serverUrl,
                schoolName = payload?.school ?: "SMAN 1 Kepanjen"
            )
        )

        _errorMessage.value = null
        _currentScreen.value = ScreenState.MOODLE_WEBVIEW
    }

    fun requestUnlockExam() {
        _adminAuthMode.value = AdminAuthMode.EXIT_EXAM
        _pendingAdminDestination.value = ScreenState.INITIAL_SETUP
        _showAdminUnlockDialog.value = true
    }

    fun dismissAdminUnlockDialog() {
        _showAdminUnlockDialog.value = false
        _pendingAdminDestination.value = null
    }

    fun lockScreenDueToBreach() {
        val currentConfig = serverConfig.value
        val isExam = _currentScreen.value == ScreenState.MOODLE_WEBVIEW || (currentConfig?.isExamActive == true)
        if (isExam) {
            _isScreenLockedByBreach.value = true
        }
    }

    fun unlockScreenFromBreachAndResume(enteredPin: String, context: android.content.Context? = null): Boolean {
        val currentAdminPin = adminPreferences.getAdminPin()
        val cleanPin = enteredPin.trim()
        // Terindikasi curang: menggunakan PIN 1ctAdm1n! (sama seperti mode admin)
        if (cleanPin == AdminPreferences.DEFAULT_ADMIN_PIN || cleanPin == currentAdminPin) {
            _isScreenLockedByBreach.value = false
            com.example.utils.SoundUtils.stopAlert()
            if (context != null) {
                com.example.utils.SoundUtils.playProctorPinSuccessUnlockSound(context)
            }
            return true
        }
        return false
    }

    fun unlockScreenFromBreachAndExit(enteredPin: String, context: android.content.Context? = null): Boolean {
        val currentAdminPin = adminPreferences.getAdminPin()
        val cleanPin = enteredPin.trim()
        // Terindikasi curang: menggunakan PIN 1ctAdm1n! (sama seperti mode admin)
        if (cleanPin == AdminPreferences.DEFAULT_ADMIN_PIN || cleanPin == currentAdminPin) {
            _isScreenLockedByBreach.value = false
            _showAdminUnlockDialog.value = false
            _pendingAdminDestination.value = null
            com.example.utils.SoundUtils.stopAlert()
            if (context != null) {
                com.example.utils.SoundUtils.playProctorPinSuccessUnlockSound(context)
            }

            viewModelScope.launch {
                configDao.deleteServerConfig()
                historyDao.clearHistory()
                _validationResult.value = null
                _parsedPayload.value = null
                _currentScreen.value = ScreenState.INITIAL_SETUP
            }
            return true
        }
        return false
    }

    fun verifyAdminPinAndUnlock(enteredPin: String, context: android.content.Context? = null): Boolean {
        val cleanPin = enteredPin.trim()
        val isExitMode = _adminAuthMode.value == AdminAuthMode.EXIT_EXAM

        // Membedakan PIN:
        // Jika keluar normal dari ujian: menggunakan PIN R4hasi@
        // Jika masuk ke mode admin: menggunakan PIN 1ctAdm1n!
        val isPinValid = if (isExitMode) {
            val currentExitPin = adminPreferences.getExitPin()
            cleanPin == AdminPreferences.DEFAULT_EXIT_PIN || cleanPin == currentExitPin
        } else {
            val currentAdminPin = adminPreferences.getAdminPin()
            cleanPin == AdminPreferences.DEFAULT_ADMIN_PIN || cleanPin == currentAdminPin
        }

        if (isPinValid) {
            val wasInExam = _currentScreen.value == ScreenState.MOODLE_WEBVIEW || (serverConfig.value?.isExamActive == true)
            val target = _pendingAdminDestination.value ?: ScreenState.INITIAL_SETUP
            _pendingAdminDestination.value = null
            _showAdminUnlockDialog.value = false
            _isScreenLockedByBreach.value = false
            com.example.utils.SoundUtils.stopAlert()

            if (wasInExam && context != null) {
                com.example.utils.SoundUtils.playProctorPinSuccessUnlockSound(context)
            }

            viewModelScope.launch {
                if (wasInExam) {
                    configDao.deleteServerConfig()
                    historyDao.clearHistory()
                    _validationResult.value = null
                    _parsedPayload.value = null
                }
                _currentScreen.value = target
            }
            return true
        }
        return false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun addAllowedDomain(domainInput: String) {
        val raw = domainInput.trim()
        if (raw.isBlank()) return

        val cleanDomain = try {
            val temp = if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                "http://$raw"
            } else {
                raw
            }
            android.net.Uri.parse(temp).host?.lowercase()?.trim()
        } catch (_: Exception) {
            null
        } ?: raw.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .split("/").firstOrNull()
            ?.split(":")?.firstOrNull()
            ?.trim() ?: ""

        if (cleanDomain.isBlank()) return

        viewModelScope.launch {
            domainDao.insertDomain(
                AllowedDomainEntity(domain = cleanDomain, isDefault = false)
            )
        }
    }

    fun removeAllowedDomain(domain: String) {
        viewModelScope.launch {
            domainDao.deleteDomain(domain)
        }
    }

    fun updateAdminPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.length >= 4) {
            adminPreferences.setAdminPin(trimmed)
            viewModelScope.launch {
                configDao.updateAdminPin(trimmed)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun toggleThemeMode() {
        themePreferences.toggleLightDark()
    }

    /**
     * Menyimpan QR Code yang baru digenerate ke daftar QR aktif.
     */
    fun saveGeneratedQr(
        examName: String,
        examCode: String,
        serverUrl: String,
        schoolName: String,
        qrPayload: String,
        isEncrypted: Boolean,
        sessionPin: String?,
        expiresAt: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            savedQrDao.deleteExpiredQrs(now)
            savedQrDao.insertSavedQr(
                SavedQrEntity(
                    examName = examName.ifBlank { "Ujian Sekolah" },
                    examCode = examCode.ifBlank { "EXAM-001" },
                    serverUrl = serverUrl,
                    schoolName = schoolName,
                    qrPayload = qrPayload,
                    isEncrypted = isEncrypted,
                    sessionPin = sessionPin?.trim()?.ifBlank { null },
                    createdAt = now,
                    expiresAt = expiresAt
                )
            )
            _refreshTicker.value = System.currentTimeMillis()
        }
    }

    /**
     * Menghapus QR Code yang tersimpan secara manual jika pengawas ingin menghapusnya lebih awal.
     */
    fun deleteSavedQr(id: Long) {
        viewModelScope.launch {
            savedQrDao.deleteSavedQr(id)
            _refreshTicker.value = System.currentTimeMillis()
        }
    }

    /**
     * Memperbarui daftar dan memicu pembersihan expired QR.
     */
    fun refreshActiveSavedQrs() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            savedQrDao.deleteExpiredQrs(now)
            _refreshTicker.value = now
        }
    }
}
