package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.AutoSaveState
import com.example.security.ExamAutoSaveBridge
import com.example.security.formatAutoSaveTimestamp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.example.security.DisabledActionModeCallback
import com.example.security.SecureExamWebViewClient
import com.example.ui.theme.SleekBackground
import com.example.ui.theme.AppTheme
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekError
import com.example.ui.theme.SleekErrorBg
import com.example.ui.theme.SleekNavyHeader
import com.example.ui.theme.SleekPrimaryBlue
import com.example.ui.theme.SleekSecondaryBlueContainer
import com.example.ui.theme.SleekSuccess
import com.example.ui.theme.SleekSuccessBg
import com.example.ui.theme.SleekSurface
import com.example.ui.theme.SleekSurfaceVariant
import com.example.ui.theme.SleekTextMuted
import com.example.ui.theme.SleekTextPrimary
import com.example.ui.theme.SleekTextSecondary
import com.example.ui.theme.SleekWarning
import com.example.ui.theme.SleekWarningBg
import com.example.utils.BatteryInfo
import com.example.utils.ConnectionType
import com.example.utils.NetworkConnectionState
import com.example.utils.rememberBatteryState
import com.example.utils.rememberNetworkConnectivityState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MoodleWebViewScreen(
    serverUrl: String,
    examCode: String?,
    allowedDomains: List<String>,
    onRequestUnlock: () -> Unit,
    onToggleTheme: () -> Unit = {},
    onDialogVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    val autoSaveBridge = remember(context) { ExamAutoSaveBridge(context) }
    val autoSaveState by autoSaveBridge.autoSaveState.collectAsStateWithLifecycle()
    var showAutoSaveInfoDialog by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(serverUrl) }

    // Enforce WindowManager FLAG_SECURE to prevent screenshots and screen recordings
    DisposableEffect(context) {
        view.keepScreenOn = true
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            view.keepScreenOn = false
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            webViewRef?.clearHistory()
            webViewRef?.clearCache(true)
        }
    }

    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var showLowBatteryDialog by remember { mutableStateOf(false) }
    var showConnectionLostDialog by remember { mutableStateOf(false) }

    // Notify parent activity when any dialog is open so window focus shifts do not trigger unpin alarms
    val isAnyInternalDialogOpen = showExitConfirmationDialog || showAutoSaveInfoDialog || showLowBatteryDialog || showConnectionLostDialog
    LaunchedEffect(isAnyInternalDialogOpen) {
        onDialogVisibilityChanged(isAnyInternalDialogOpen)
    }

    var backPressCount by remember { mutableStateOf(0) }
    // Intercept back button during active exam - notify that back button is prohibited
    BackHandler(enabled = true) {
        backPressCount++
        if (backPressCount >= 2) {
            (context as? com.example.MainActivity)?.triggerUnpinLoudAlert(
                "PERINGATAN: Terindikasi curang dengan menekan tombol Back saat ujian!"
            )
        } else {
            Toast.makeText(
                context,
                "Peringatan: Dilarang menekan tombol Back saat ujian! Tekan sekali lagi akan mengunci layar.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val activeHost = remember(serverUrl) {
        try {
            Uri.parse(serverUrl).host?.lowercase()?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val finalTargetUrl = remember(serverUrl, examCode) {
        if (!examCode.isNullOrBlank()) {
            if (serverUrl.contains("?")) {
                "$serverUrl&code=$examCode"
            } else {
                "$serverUrl?code=$examCode"
            }
        } else {
            serverUrl
        }
    }

    androidx.compose.runtime.LaunchedEffect(finalTargetUrl) {
        if (webViewRef?.url != finalTargetUrl) {
            webViewRef?.loadUrl(finalTargetUrl)
        }
    }

    val batteryInfo by rememberBatteryState()
    var isWarningBannerDismissed by remember { mutableStateOf(false) }
    var lastAlertedBatteryLevel by remember { mutableStateOf<Int?>(null) }

    val networkState by rememberNetworkConnectivityState()
    var wasPreviouslyConnected by remember { mutableStateOf(true) }

    // Alert student when internet connection is lost completely (allows both Wi-Fi and mobile data)
    LaunchedEffect(networkState.isConnected) {
        if (!networkState.isConnected) {
            if (wasPreviouslyConnected) {
                showConnectionLostDialog = true
            }
            wasPreviouslyConnected = false
        } else {
            showConnectionLostDialog = false
            wasPreviouslyConnected = true
        }
    }

    // Reset dismissal when battery goes from safe to low or drops to critical
    LaunchedEffect(batteryInfo.percentage, batteryInfo.isCharging) {
        if (batteryInfo.isCharging) {
            isWarningBannerDismissed = false
            showLowBatteryDialog = false
        } else if (batteryInfo.isLow) {
            if (lastAlertedBatteryLevel == null || 
                (lastAlertedBatteryLevel!! > 15 && batteryInfo.percentage <= 15) || 
                (lastAlertedBatteryLevel!! > 10 && batteryInfo.percentage <= 10)) {
                showLowBatteryDialog = true
                isWarningBannerDismissed = false
                lastAlertedBatteryLevel = batteryInfo.percentage
            }
        } else {
            showLowBatteryDialog = false
            isWarningBannerDismissed = false
            lastAlertedBatteryLevel = batteryInfo.percentage
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SleekBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Lock Status Bar
            Surface(
                color = SleekSurface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(1.dp, SleekBorder)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isVeryCompact = maxWidth < 360.dp
                    val isCompact = maxWidth < 400.dp
                    val horizontalPad = if (isVeryCompact) 6.dp else 10.dp
                    val itemSpacing = if (isVeryCompact) 3.dp else 5.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Security Locked Badge
                        Surface(
                            color = SleekSuccessBg,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SleekSuccess.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isVeryCompact) 6.dp else 8.dp,
                                    vertical = if (isVeryCompact) 4.dp else 5.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Status Kiosk Aktif",
                                    tint = SleekSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isVeryCompact) "KUNCI" else "TERKUNCI",
                                    color = SleekSuccess,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (isVeryCompact) 10.sp else 11.sp,
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = false))
                        Spacer(modifier = Modifier.width(itemSpacing))

                        // Real-time Wi-Fi / Network Connectivity Indicator Chip
                        NetworkConnectivityChip(
                            networkState = networkState,
                            compactMode = isCompact
                        )

                        Spacer(modifier = Modifier.width(itemSpacing))

                        // Live Battery Status Badge
                        BatteryIndicatorChip(
                            batteryInfo = batteryInfo,
                            compactMode = isCompact
                        )

                        Spacer(modifier = Modifier.width(itemSpacing))

                        // Reload / Refresh Page Button
                        Surface(
                            onClick = {
                                webViewRef?.reload()
                                Toast.makeText(context, "Memuat ulang halaman ujian...", Toast.LENGTH_SHORT).show()
                            },
                            color = SleekSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorder),
                            modifier = Modifier.size(if (isVeryCompact) 28.dp else 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Muat Ulang Halaman",
                                    tint = SleekNavyHeader,
                                    modifier = Modifier.size(if (isVeryCompact) 14.dp else 16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(if (isVeryCompact) 4.dp else 6.dp))

                        // Responsive Keluar Button that never truncates on small phones
                        Surface(
                            onClick = { showExitConfirmationDialog = true },
                            color = SleekError,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isVeryCompact) 7.dp else 9.dp,
                                    vertical = if (isVeryCompact) 5.dp else 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Keluar Ujian",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.5.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Keluar",
                                    fontSize = if (isVeryCompact) 10.5.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Connection Lost Warning Banner during active exam - only if completely disconnected
            AnimatedVisibility(
                visible = !networkState.isConnected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                NetworkDisconnectedWarningBanner(
                    networkState = networkState,
                    onRetry = { webViewRef?.reload() }
                )
            }

            // Low Battery Warning Banner during active exam
            AnimatedVisibility(
                visible = batteryInfo.isLow && !isWarningBannerDismissed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LowBatteryWarningBanner(
                    batteryInfo = batteryInfo,
                    onDismiss = { isWarningBannerDismissed = true }
                )
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = SleekPrimaryBlue,
                    trackColor = SleekBorder
                )
            }

            // WebView Component container with solid white canvas to prevent black blank flashes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        object : WebView(ctx) {
                            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null
                            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null
                        }.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // 1. Force white background so it NEVER flashes or renders black
                            setBackgroundColor(android.graphics.Color.WHITE)

                            // 2. WebSettings configuration
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.setSupportZoom(true)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(false)
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ExamBrowser/1.0"
                            settings.textZoom = 100
                            settings.mediaPlaybackRequiresUserGesture = false

                            // Disable automatic dark mode color inversion which causes black blank screens in web exam systems (Moodle/CBT)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                settings.forceDark = WebSettings.FORCE_DARK_OFF
                            }

                            isLongClickable = false
                            isHapticFeedbackEnabled = false
                            setOnLongClickListener { true }
                            setOnCreateContextMenuListener { menu, _, _ -> menu?.clear() }

                            addJavascriptInterface(autoSaveBridge, ExamAutoSaveBridge.JAVASCRIPT_OBJ_NAME)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadingProgress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    try {
                                        android.app.AlertDialog.Builder(ctx)
                                            .setTitle("Pemberitahuan Ujian")
                                            .setMessage(message ?: "")
                                            .setPositiveButton("OK") { _, _ -> result?.confirm() }
                                            .setOnCancelListener { result?.confirm() }
                                            .setCancelable(false)
                                            .show()
                                    } catch (_: Exception) {
                                        result?.confirm()
                                    }
                                    return true
                                }

                                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    try {
                                        android.app.AlertDialog.Builder(ctx)
                                            .setTitle("Konfirmasi Ujian")
                                            .setMessage(message ?: "")
                                            .setPositiveButton("Lanjutkan") { _, _ -> result?.confirm() }
                                            .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                                            .setOnCancelListener { result?.cancel() }
                                            .setCancelable(false)
                                            .show()
                                    } catch (_: Exception) {
                                        result?.confirm()
                                    }
                                    return true
                                }

                                override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    // Allow question page transition without getting stuck
                                    result?.confirm()
                                    return true
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    return true
                                }
                            }

                            webViewClient = SecureExamWebViewClient(
                                onLoadingChanged = { loading -> isLoading = loading },
                                onUrlChanged = { url -> currentUrl = url }
                            )

                            loadUrl(finalTargetUrl)
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        if (webView.url != finalTargetUrl && (webView.url == null || webView.url == "about:blank")) {
                            webView.loadUrl(finalTargetUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Auto-Save Info Modal Dialog
        if (showAutoSaveInfoDialog) {
            AutoSaveInfoDialog(
                autoSaveState = autoSaveState,
                onManualSave = {
                    webViewRef?.evaluateJavascript("if (window.__triggerManualAutoSave) { window.__triggerManualAutoSave(); }", null)
                    Toast.makeText(context, "Mencadangkan respon sekarang...", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showAutoSaveInfoDialog = false }
            )
        }

        // Formal Exit Confirmation Modal Dialog
        if (showExitConfirmationDialog) {
            ExitExamConfirmationDialog(
                onConfirm = {
                    showExitConfirmationDialog = false
                    onRequestUnlock()
                },
                onDismiss = { showExitConfirmationDialog = false }
            )
        }

        // Low Battery Modal Alert Dialog
        if (showLowBatteryDialog) {
            LowBatteryAlertDialog(
                batteryPercentage = batteryInfo.percentage,
                onDismiss = { showLowBatteryDialog = false }
            )
        }

        // Connection Lost Modal Alert Dialog
        if (showConnectionLostDialog) {
            ConnectionLostAlertDialog(
                networkState = networkState,
                onDismiss = { showConnectionLostDialog = false },
                onRetry = {
                    showConnectionLostDialog = false
                    webViewRef?.reload()
                }
            )
        }
    }
}

@Composable
fun NetworkConnectivityChip(
    networkState: NetworkConnectionState,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isOnline = networkState.isConnected

    val containerColor = when {
        !isOnline -> SleekErrorBg
        networkState.isWifi -> SleekSuccessBg
        else -> SleekSecondaryBlueContainer
    }

    val contentColor = when {
        !isOnline -> SleekError
        networkState.isWifi -> SleekSuccess
        else -> SleekPrimaryBlue
    }

    val labelText = when {
        !isOnline -> "Terputus"
        networkState.isWifi -> "Wi-Fi"
        else -> "Paket Data"
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compactMode) 5.dp else 8.dp,
                vertical = if (compactMode) 4.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val strokeW = 1.3.dp.toPx()
                val center = Offset(size.width / 2f, size.height * 0.85f)

                if (isOnline) {
                    if (networkState.isWifi) {
                        // Wi-Fi dot & waves
                        drawCircle(color = contentColor, radius = 1.5.dp.toPx(), center = center)
                        drawArc(
                            color = contentColor,
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(center.x - 3.5.dp.toPx(), center.y - 4.5.dp.toPx()),
                            size = Size(7.dp.toPx(), 7.dp.toPx()),
                            style = Stroke(width = strokeW)
                        )
                        drawArc(
                            color = contentColor,
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(center.x - 6.dp.toPx(), center.y - 7.5.dp.toPx()),
                            size = Size(12.dp.toPx(), 12.dp.toPx()),
                            style = Stroke(width = strokeW)
                        )
                    } else {
                        // Mobile data signal bars
                        val barW = 2.dp.toPx()
                        val spacing = 1.2.dp.toPx()
                        val h1 = 3.5.dp.toPx()
                        val h2 = 6.5.dp.toPx()
                        val h3 = 9.5.dp.toPx()
                        val baseY = size.height - 1.dp.toPx()
                        val startX = 1.5.dp.toPx()

                        drawRect(color = contentColor, topLeft = Offset(startX, baseY - h1), size = Size(barW, h1))
                        drawRect(color = contentColor, topLeft = Offset(startX + barW + spacing, baseY - h2), size = Size(barW, h2))
                        drawRect(color = contentColor, topLeft = Offset(startX + (barW + spacing) * 2, baseY - h3), size = Size(barW, h3))
                    }
                } else {
                    // Disconnected / Slash line
                    drawCircle(color = contentColor, radius = 1.5.dp.toPx(), center = center)
                    drawArc(
                        color = contentColor.copy(alpha = 0.5f),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - 5.5.dp.toPx(), center.y - 7.dp.toPx()),
                        size = Size(11.dp.toPx(), 11.dp.toPx()),
                        style = Stroke(width = strokeW)
                    )
                    drawLine(
                        color = contentColor,
                        start = Offset(1.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.width(3.dp))

            Text(
                text = labelText,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (compactMode) 10.sp else 11.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun NetworkDisconnectedWarningBanner(
    networkState: NetworkConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SleekErrorBg,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SleekError.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SleekError,
                shape = CircleShape,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan Koneksi",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KONEKSI INTERNET TERPUTUS",
                    color = SleekError,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Tidak ada koneksi internet (Wi-Fi maupun Paket Data terputus). Jawaban Anda mungkin belum tersimpan ke server. Pastikan koneksi internet aktif kembali.",
                    color = SleekTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleekError,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("MUAT ULANG", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ConnectionLostAlertDialog(
    networkState: NetworkConnectionState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        icon = {
            Surface(
                color = SleekErrorBg,
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SleekError,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Koneksi Internet Terputus!",
                color = SleekNavyHeader,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Koneksi internet (baik Wi-Fi maupun Paket Data seluler) pada perangkat Anda terputus.\n\n" +
                        "1. Harap tetap berada di aplikasi ini dan jangan keluar.\n" +
                        "2. Pastikan Paket Data seluler atau Wi-Fi perangkat Anda aktif kembali.\n" +
                        "3. Beritahu pengawas ruang jika koneksi internet tetap tidak tersambung.\n\n" +
                        "Setelah terhubung kembali, tekan 'Muat Ulang Halaman' di bawah.",
                color = SleekTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Muat Ulang Halaman", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup Notifikasi", color = SleekTextMuted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun BatteryIndicatorChip(
    batteryInfo: BatteryInfo,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        batteryInfo.isCharging -> SleekSuccessBg
        batteryInfo.percentage <= 15 -> SleekErrorBg
        batteryInfo.percentage <= 25 -> SleekWarningBg
        else -> SleekSurfaceVariant
    }

    val contentColor = when {
        batteryInfo.isCharging -> SleekSuccess
        batteryInfo.percentage <= 15 -> SleekError
        batteryInfo.percentage <= 25 -> SleekWarning
        else -> SleekTextSecondary
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compactMode) 5.dp else 8.dp,
                vertical = if (compactMode) 4.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(width = 15.dp, height = 9.5.dp)) {
                val strokeWidth = 1.1.dp.toPx()
                val width = size.width
                val height = size.height
                val bodyWidth = width - 2.2.dp.toPx()

                // Battery body frame
                drawRoundRect(
                    color = contentColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(bodyWidth, height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )

                // Battery terminal cap
                drawRoundRect(
                    color = contentColor,
                    topLeft = Offset(bodyWidth + 0.4.dp.toPx(), height * 0.25f),
                    size = Size(1.8.dp.toPx(), height * 0.5f),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Battery fill level
                val fillRatio = (batteryInfo.percentage.coerceIn(0, 100) / 100f)
                val innerPadding = 1.8.dp.toPx()
                val maxFillWidth = bodyWidth - (innerPadding * 2)
                if (maxFillWidth > 0 && fillRatio > 0) {
                    drawRoundRect(
                        color = contentColor,
                        topLeft = Offset(innerPadding, innerPadding),
                        size = Size(maxFillWidth * fillRatio, height - (innerPadding * 2)),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.width(3.dp))

            Text(
                text = if (batteryInfo.isCharging) "⚡${batteryInfo.percentage}%" else "${batteryInfo.percentage}%",
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (compactMode) 10.sp else 11.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun LowBatteryWarningBanner(
    batteryInfo: BatteryInfo,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SleekErrorBg,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SleekError.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SleekError,
                shape = CircleShape,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan Baterai Lemah",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BATERAI LEMAH (${batteryInfo.percentage}%) - HUBUNGKAN CHARGER",
                    color = SleekError,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Baterai di bawah 15%. Harap segera isi daya agar perangkat tidak mati saat ujian.",
                    color = SleekTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = SleekError,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LowBatteryAlertDialog(
    batteryPercentage: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        icon = {
            Surface(
                color = SleekErrorBg,
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SleekError,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Peringatan: Baterai Lemah ($batteryPercentage%)",
                color = SleekNavyHeader,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Sisa baterai perangkat Anda tinggal $batteryPercentage%.\n\nHarap segera hubungkan perangkat ke pengisi daya (charger) agar tidak terjadi mati mendadak dan sesi ujian Anda tidak terputus.",
                color = SleekTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Saya Mengerti, Hubungkan Charger", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun ExitExamConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        icon = {
            Surface(
                color = SleekErrorBg,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = SleekError,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Keluar dari Ujian",
                color = SleekNavyHeader,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Mode Ujian Terkunci (Task Lock) sedang aktif untuk menjaga integritas ujian.",
                    color = SleekTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SleekWarningBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SleekWarning,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pastikan seluruh lembar ujian telah disubmit. Keluar dari browser memerlukan Password / PIN dari Pengawas.",
                            color = SleekNavyHeader,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleekError,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Buka Kunci (PIN Pengawas)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Batal (Tetap di Ujian)", color = SleekTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun AutoSaveIndicatorChip(
    autoSaveState: AutoSaveState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSaving = autoSaveState.isSavingAnimation
    val isSaved = autoSaveState.lastSavedTimestamp != null

    val containerColor = when {
        isSaving -> SleekSecondaryBlueContainer
        isSaved -> SleekSuccessBg
        else -> SleekSurfaceVariant
    }

    val contentColor = when {
        isSaving -> SleekPrimaryBlue
        isSaved -> SleekSuccess
        else -> SleekTextSecondary
    }

    val labelText = when {
        isSaving -> "Menyimpan..."
        isSaved && autoSaveState.savedFieldsCount > 0 -> "Draft (${autoSaveState.savedFieldsCount})"
        isSaved -> "Draft Aman"
        else -> "Auto-Save"
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSaving) SleekPrimaryBlue else SleekBorder),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSaving) Icons.Default.Sync else Icons.Default.Save,
                contentDescription = "Status Auto-Save Respon",
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = labelText,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AutoSaveInfoDialog(
    autoSaveState: AutoSaveState,
    onManualSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        icon = {
            Surface(
                color = SleekSecondaryBlueContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = SleekPrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Pencadangan Respon Ujian (Auto-Save)",
                color = SleekNavyHeader,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Aplikasi secara berkala (setiap 3 detik) memantau dan menyimpan seluruh jawaban Anda (pilihan ganda, esai, checkbox, isian angka).",
                    color = SleekTextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = SleekSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status Auto-Save:",
                                color = SleekTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatAutoSaveTimestamp(autoSaveState.lastSavedTimestamp),
                                color = SleekSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (autoSaveState.savedFieldsCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Jumlah Isian Terdeteksi:",
                                    color = SleekTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${autoSaveState.savedFieldsCount} input",
                                    color = SleekNavyHeader,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = SleekSuccessBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SleekSuccess,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Jika aplikasi tertutup tidak sengaja atau Wi-Fi mati, jawaban Anda akan langsung dipulihkan secara otomatis saat membuka kembali halaman ujian.",
                            color = SleekNavyHeader,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onManualSave()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paksa Simpan Sekarang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tutup", color = SleekTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    )
}

