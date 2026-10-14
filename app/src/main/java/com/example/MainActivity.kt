package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camera.QrCameraScannerView
import com.example.ui.dialogs.AdminUnlockDialog
import com.example.ui.dialogs.SessionPinPromptDialog
import com.example.ui.screens.AdminToolsScreen
import com.example.ui.screens.BreachLockoutScreen
import com.example.ui.screens.InitialSetupScreen
import com.example.ui.screens.MoodleWebViewScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ExamBrowserViewModel
import com.example.viewmodel.ScreenState

class MainActivity : ComponentActivity() {

    private var isExamActiveState = false
    private var isUnlockDialogOpenState = false
    private var examStartTime = 0L
    private var activeViewModel: ExamBrowserViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ExamBrowserViewModel = viewModel()
            activeViewModel = viewModel
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExamBrowserApp(
                        viewModel = viewModel,
                        onExamStateChanged = { isActive, isUnlockDialogOpen ->
                            if (isActive && !isExamActiveState) {
                                examStartTime = System.currentTimeMillis()
                            }
                            isExamActiveState = isActive
                            isUnlockDialogOpenState = isUnlockDialogOpen
                            updateSecurityAndKioskMode(isActive)
                        }
                    )
                }
            }
        }
    }

    private fun triggerNavButtonNotificationOnly(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun triggerUnpinLoudAlert(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        activeViewModel?.lockScreenDueToBreach()
        com.example.utils.SoundUtils.playForcedUnpinBreachSiren(this)
    }

    fun reApplySecurityAndKiosk() {
        updateSecurityAndKioskMode(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (isExamActiveState && hasFocus) {
            // Re-apply fullscreen and kiosk security when window regains focus
            updateSecurityAndKioskMode(true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isExamActiveState && !isUnlockDialogOpenState) {
            val now = System.currentTimeMillis()
            val isGracePeriod = (now - examStartTime < 5000L)
            if (!isGracePeriod) {
                // User navigated away via Recent Apps, Home, or Unpinning gesture
                triggerUnpinLoudAlert("PERINGATAN: Dilarang melepas sematan atau keluar dari ujian!")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isExamActiveState && !isUnlockDialogOpenState) {
            val now = System.currentTimeMillis()
            val isGracePeriod = (now - examStartTime < 5000L)
            if (!isGracePeriod) {
                triggerUnpinLoudAlert("PERINGATAN: Dilarang keluar dari aplikasi ujian!")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isExamActiveState) {
            updateSecurityAndKioskMode(true)
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am?.lockTaskModeState == android.app.ActivityManager.LOCK_TASK_MODE_NONE && !isUnlockDialogOpenState) {
                val now = System.currentTimeMillis()
                if (now - examStartTime >= 5000L) {
                    triggerUnpinLoudAlert("PERINGATAN: Sematan aplikasi terlepas! Kunci ulang diaktifkan.")
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isExamActiveState && !isUnlockDialogOpenState) {
            val now = System.currentTimeMillis()
            val isGracePeriod = (now - examStartTime < 5000L)
            if (!isGracePeriod) {
                // User pressed Recent + Back / Home to unpin or leave app
                triggerUnpinLoudAlert("PERINGATAN: Dilarang melepas sematan aplikasi!")
            }
            updateSecurityAndKioskMode(true)
            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (isExamActiveState && !isUnlockDialogOpenState) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_APP_SWITCH -> {
                    triggerUnpinLoudAlert("PERINGATAN: Dilarang menekan tombol Home atau Recent Apps saat ujian!")
                    return true
                }
                android.view.KeyEvent.KEYCODE_BACK -> {
                    return super.onKeyDown(keyCode, event)
                }
                android.view.KeyEvent.KEYCODE_MENU -> {
                    triggerNavButtonNotificationOnly("PERINGATAN: Dilarang membuka menu sistem saat ujian!")
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateSecurityAndKioskMode(isExamActive: Boolean) {
        if (isExamActive) {
            // Prevent screenshots, screen recording, and task switcher preview
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            hideSystemUI()

            // Lock Task Mode (Kiosk Pin App)
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (am?.lockTaskModeState == android.app.ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask()
                }
            } catch (_: Exception) {
            }
        } else {
            // Clear FLAG_SECURE when back in normal setup/admin mode
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            showSystemUI()

            // Exit Lock Task Mode
            try {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (am?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE) {
                    stopLockTask()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun showSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun ExamBrowserApp(
    viewModel: ExamBrowserViewModel = viewModel(),
    onExamStateChanged: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val allowedDomains by viewModel.allowedDomains.collectAsStateWithLifecycle()
    val serverHistory by viewModel.serverHistory.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidating.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val showUnlockDialog by viewModel.showAdminUnlockDialog.collectAsStateWithLifecycle()
    val adminAuthMode by viewModel.adminAuthMode.collectAsStateWithLifecycle()
    val isScreenLockedByBreach by viewModel.isScreenLockedByBreach.collectAsStateWithLifecycle()
    val pendingPinQrCode by viewModel.pendingPinQrCode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currentAdminPin by viewModel.adminPinFlow.collectAsStateWithLifecycle()
    val activeSavedQrs by viewModel.activeSavedQrs.collectAsStateWithLifecycle()
    var isExamInternalDialogOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val isExamActive = currentScreen == ScreenState.MOODLE_WEBVIEW && (serverConfig?.isExamActive == true)
    val isAnyDialogOpen = showUnlockDialog || isExamInternalDialogOpen || isScreenLockedByBreach || (pendingPinQrCode != null)

    LaunchedEffect(isExamActive, isAnyDialogOpen) {
        onExamStateChanged(isExamActive, isAnyDialogOpen)
    }

    when (currentScreen) {
        ScreenState.INITIAL_SETUP -> {
            InitialSetupScreen(
                lastServerConfig = serverConfig,
                serverHistory = serverHistory,
                errorMessage = errorMessage,
                isValidating = isValidating,
                themeMode = themeMode,
                onConnectUrl = { url -> viewModel.validateAndPreviewUrl(url) },
                onOpenQrScanner = { viewModel.openQrScanner() },
                onOpenAdminTools = { viewModel.navigateTo(ScreenState.ADMIN_TOOLS) },
                onClearError = { viewModel.clearErrorMessage() },
                onToggleTheme = { viewModel.toggleThemeMode() }
            )
        }

        ScreenState.QR_SCANNER -> {
            val pendingAdmin = viewModel.pendingAdminDestination.collectAsStateWithLifecycle().value
            QrCameraScannerView(
                onQrCodeScanned = { qrText -> viewModel.processScannedQr(qrText) },
                onClose = {
                    if (pendingAdmin != null) {
                        viewModel.navigateTo(ScreenState.MOODLE_WEBVIEW)
                    } else {
                        viewModel.navigateTo(ScreenState.INITIAL_SETUP)
                    }
                }
            )
        }

        ScreenState.MOODLE_WEBVIEW -> {
            MoodleWebViewScreen(
                serverUrl = serverConfig?.serverUrl ?: "",
                examCode = serverConfig?.examCode,
                allowedDomains = allowedDomains.map { it.domain },
                onRequestUnlock = { viewModel.requestUnlockExam() },
                onToggleTheme = { viewModel.toggleThemeMode() },
                onDialogVisibilityChanged = { isExamInternalDialogOpen = it }
            )
        }

        ScreenState.ADMIN_TOOLS -> {
            AdminToolsScreen(
                onBack = { viewModel.navigateTo(ScreenState.INITIAL_SETUP) },
                activeSavedQrs = activeSavedQrs,
                onSaveGeneratedQr = { examName, examCode, serverUrl, schoolName, qrPayload, isEncrypted, sessionPin, expiresAt ->
                    viewModel.saveGeneratedQr(
                        examName, examCode, serverUrl, schoolName, qrPayload, isEncrypted, sessionPin, expiresAt
                    )
                },
                onDeleteSavedQr = { id -> viewModel.deleteSavedQr(id) },
                onRefreshSavedQrs = { viewModel.refreshActiveSavedQrs() }
            )
        }
    }

    if (isScreenLockedByBreach) {
        val context = androidx.compose.ui.platform.LocalContext.current
        BreachLockoutScreen(
            onUnlockAndResume = { key ->
                val success = viewModel.unlockScreenFromBreachAndResume(key, context)
                if (success) {
                    (context as? MainActivity)?.reApplySecurityAndKiosk()
                }
                success
            },
            onUnlockAndExit = { key ->
                viewModel.unlockScreenFromBreachAndExit(key, context)
            }
        )
    } else if (pendingPinQrCode != null) {
        SessionPinPromptDialog(
            onSubmitPin = { pin -> viewModel.submitSessionPinForQr(pin) },
            onDismiss = { viewModel.cancelSessionPinPrompt() }
        )
    } else if (showUnlockDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AdminUnlockDialog(
            mode = adminAuthMode,
            onVerifyPin = { pin -> viewModel.verifyAdminPinAndUnlock(pin, context) },
            onOpenQrScan = {
                viewModel.dismissAdminUnlockDialog()
                viewModel.openQrScanner()
            },
            onDismiss = { viewModel.dismissAdminUnlockDialog() }
        )
    }
}
