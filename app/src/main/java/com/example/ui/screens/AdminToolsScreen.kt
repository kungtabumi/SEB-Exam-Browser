package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedQrEntity
import com.example.qr.QrGenerator
import com.example.qr.QrPrintData
import com.example.security.QrPayloadParser
import com.example.security.SecureQrCrypto
import com.example.ui.dialogs.QrDetailDialog
import com.example.ui.dialogs.QrPrintPdfDialog
import com.example.ui.theme.SleekBackground
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekInputBg
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminToolsScreen(
    onBack: () -> Unit,
    activeSavedQrs: List<SavedQrEntity> = emptyList(),
    onSaveGeneratedQr: (
        examName: String,
        examCode: String,
        serverUrl: String,
        schoolName: String,
        qrPayload: String,
        isEncrypted: Boolean,
        sessionPin: String?,
        expiresAt: Long
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteSavedQr: (Long) -> Unit = {},
    onRefreshSavedQrs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var viewingQrForDetail by remember { mutableStateOf<SavedQrEntity?>(null) }
    var printingQrData by remember { mutableStateOf<QrPrintData?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface)
                    .border(1.dp, SleekBorder)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = SleekNavyHeader
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mode Admin CBT",
                        color = SleekNavyHeader,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Generator & Manajemen QR Code Ujian",
                        color = SleekTextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Tab Navigation: Generator vs QR Aktif Tersimpan
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SleekSurface,
                contentColor = SleekPrimaryBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = SleekPrimaryBlue,
                        height = 3.dp
                    )
                },
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SleekBorder)
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buat QR Ujian", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        onRefreshSavedQrs()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdded,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("QR Aktif Tersimpan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                            if (activeSavedQrs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = SleekPrimaryBlue,
                                    shape = CircleShape,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = activeSavedQrs.size.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            GeneratorTabContent(
                                onSaveQr = { examName, examCode, serverUrl, schoolName, payload, isEncrypted, sessionPin, expiresAt ->
                                    onSaveGeneratedQr(
                                        examName, examCode, serverUrl, schoolName, payload, isEncrypted, sessionPin, expiresAt
                                    )
                                    // Beralih otomatis ke tab daftar tersimpan
                                    selectedTabIndex = 1
                                },
                                onPrintQr = { printingQrData = it }
                            )
                        }
                        1 -> {
                            SavedQrsTabContent(
                                activeSavedQrs = activeSavedQrs,
                                onRefresh = onRefreshSavedQrs,
                                onDelete = onDeleteSavedQr,
                                onViewDetail = { viewingQrForDetail = it },
                                onPrintQr = { printingQrData = it },
                                onGoToGenerator = { selectedTabIndex = 0 }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog Detail QR Code jika pengawas mengklik kartu QR
    viewingQrForDetail?.let { qr ->
        QrDetailDialog(
            savedQr = qr,
            onDismiss = { viewingQrForDetail = null }
        )
    }

    // Modal Dialog Cetak PDF QR Code
    printingQrData?.let { data ->
        QrPrintPdfDialog(
            data = data,
            onDismiss = { printingQrData = null }
        )
    }
}

@Composable
private fun GeneratorTabContent(
    onSaveQr: (
        examName: String,
        examCode: String,
        serverUrl: String,
        schoolName: String,
        payload: String,
        isEncrypted: Boolean,
        sessionPin: String?,
        expiresAt: Long
    ) -> Unit,
    onPrintQr: (QrPrintData) -> Unit = {}
) {
    var examName by remember { mutableStateOf("Ujian Sekolah") }
    var examCode by remember { mutableStateOf("EXAM-001") }
    var serverUrl by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var isEncryptionEnabled by remember { mutableStateOf(true) }
    var sessionPin by remember { mutableStateOf("") }
    var expiryHours by remember { mutableIntStateOf(12) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sdf = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    val nowMs = remember { System.currentTimeMillis() }
    val calculatedExpiresMs = remember(expiryHours, nowMs) {
        System.currentTimeMillis() + (expiryHours * 3600L * 1000L)
    }

    val isServerUrlValid = serverUrl.isNotBlank()

    val rawJsonPayload = remember(examName, examCode, serverUrl, schoolName, expiryHours, isServerUrlValid) {
        if (!isServerUrlValid) return@remember ""
        val currentMs = System.currentTimeMillis()
        val expMs = currentMs + (expiryHours * 3600L * 1000L)

        val issuedStr = sdf.format(Date(currentMs))
        val expireStr = sdf.format(Date(expMs))

        val sig = QrPayloadParser.generateSignature(serverUrl, examCode, expireStr)

        val obj = JSONObject().apply {
            put("type", "universal_exam")
            put("version", 1)
            put("school", schoolName)
            put("server", serverUrl)
            put("examCode", examCode)
            put("examName", examName)
            put("issuedAt", issuedStr)
            put("expiresAt", expireStr)
            put("signature", sig)
        }
        obj.toString(2)
    }

    val finalPayload = remember(rawJsonPayload, isEncryptionEnabled, sessionPin, isServerUrlValid) {
        if (!isServerUrlValid || rawJsonPayload.isBlank()) return@remember ""
        if (isEncryptionEnabled) {
            try {
                SecureQrCrypto.encryptPayload(rawJsonPayload, sessionPin.trim().ifBlank { null })
            } catch (_: Exception) {
                rawJsonPayload
            }
        } else {
            rawJsonPayload
        }
    }

    val qrBitmap = remember(finalPayload, isServerUrlValid) {
        if (isServerUrlValid && finalPayload.isNotBlank()) {
            QrGenerator.generateQrBitmap(finalPayload, sizePx = 512)
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Generator QR Code Ujian Pengawas",
            color = SleekNavyHeader,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Text(
            text = "Buat QR Code terenkripsi yang eksklusif hanya dapat discan oleh aplikasi ini.",
            color = SleekTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 2.dp, bottom = 14.dp)
        )

        // Security Encryption Toggle Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isEncryptionEnabled) SleekSuccessBg else SleekSurface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isEncryptionEnabled) SleekSuccess.copy(alpha = 0.5f) else SleekBorder,
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isEncryptionEnabled) SleekSuccess else SleekPrimaryBlue,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isEncryptionEnabled) Icons.Default.Lock else Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Enkripsi Anti-Bocor (AES-256)",
                                color = SleekNavyHeader,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isEncryptionEnabled)
                                    "Hanya bisa discan via aplikasi ini"
                                else
                                    "Format terbuka (bisa discan kamera biasa)",
                                color = if (isEncryptionEnabled) SleekSuccess else SleekTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isEncryptionEnabled,
                        onCheckedChange = { isEncryptionEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SleekSuccess,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SleekBorder
                        )
                    )
                }

                if (isEncryptionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kamera biasa, Google Lens, dan aplikasi lain TIDAK AKAN BISA membaca atau membuka tautan ini. Hanya aplikasi Exam Browser ini yang dapat mendekripsi.",
                        color = SleekNavyHeader.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Optional Session PIN when Encryption is ON
        if (isEncryptionEnabled) {
            AdminInputField(
                label = "PIN Sesi Ujian (Opsional - Kosongkan jika ingin auto-buka)",
                value = sessionPin,
                onValueChange = { sessionPin = it }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (sessionPin.isBlank())
                    "✓ Tanpa PIN: Siswa cukup scan QR dan otomatis langsung terbuka di aplikasi ini."
                else
                    "🔒 Dengan PIN: Siswa harus memasukkan PIN '$sessionPin' setelah scan QR.",
                color = SleekTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Expiry selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Masa Berlaku QR Code:",
                color = SleekTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(1, 6, 12, 24, 72)
            options.forEach { hours ->
                val label = when (hours) {
                    1 -> "1 Jam"
                    6 -> "6 Jam"
                    12 -> "12 Jam"
                    24 -> "24 Jam"
                    else -> "3 Hari"
                }
                FilterChip(
                    selected = expiryHours == hours,
                    onClick = { expiryHours = hours },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SleekPrimaryBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AdminInputField(
            label = "Nama Sekolah",
            value = schoolName,
            onValueChange = { schoolName = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminInputField(
            label = "Nama Mata Pelajaran / Ujian",
            value = examName,
            onValueChange = { examName = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        AdminInputField(
            label = "Kode Ujian (Exam Code)",
            value = examCode,
            onValueChange = { examCode = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "URL Server Ujian (LMS / CBT / Web Ujian)",
                color = SleekTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            placeholder = { Text("https://ujian.sekolah.sch.id", color = SleekTextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SleekTextPrimary,
                unfocusedTextColor = SleekTextPrimary,
                focusedBorderColor = SleekPrimaryBlue,
                unfocusedBorderColor = SleekBorder,
                focusedContainerColor = SleekInputBg,
                unfocusedContainerColor = SleekInputBg
            ),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Generated QR Image Preview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .border(1.dp, SleekBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Encryption Status Badge
                Surface(
                    color = if (isEncryptionEnabled) SleekSuccessBg else SleekSecondaryBlueContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isEncryptionEnabled) SleekSuccess.copy(alpha = 0.5f) else SleekPrimaryBlue.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isEncryptionEnabled) Icons.Default.Lock else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isEncryptionEnabled) SleekSuccess else SleekPrimaryBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEncryptionEnabled)
                                "TERENKRIPSI KHUSUS APLIKASI (AES-256-GCM)"
                            else
                                "QR STANDAR (TERBUKA)",
                            color = if (isEncryptionEnabled) SleekSuccess else SleekPrimaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (qrBitmap != null) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Code Ujian Terenkripsi",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(8.dp)
                        )
                    }
                } else {
                    // Placeholder jika URL server belum diisi
                    Surface(
                        color = SleekSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorder),
                        modifier = Modifier
                            .size(220.dp)
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = SleekTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Masukkan URL Server Ujian di atas untuk menghasilkan QR Code",
                                color = SleekTextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = examName.ifBlank { "Ujian Sekolah" },
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Kode: ${examCode.ifBlank { "EXAM-001" }} • Berlaku: $expiryHours Jam",
                    color = SleekTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Cetak PDF, Simpan QR Code, & Salin Teks
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tombol CETAK PDF
                        Button(
                            onClick = {
                                if (!isServerUrlValid || finalPayload.isBlank()) {
                                    Toast.makeText(context, "Harap isi URL Server Ujian terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val printData = QrPrintData(
                                    examName = examName.ifBlank { "Ujian Sekolah" },
                                    examCode = examCode.ifBlank { "EXAM-001" },
                                    serverUrl = serverUrl,
                                    schoolName = schoolName,
                                    qrPayload = finalPayload,
                                    isEncrypted = isEncryptionEnabled,
                                    sessionPin = sessionPin.trim().ifBlank { null },
                                    expiresAt = calculatedExpiresMs
                                )
                                onPrintQr(printData)
                            },
                            enabled = isServerUrlValid && finalPayload.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleekPrimaryBlue,
                                disabledContainerColor = SleekBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cetak PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Tombol SIMPAN QR CODE
                        Button(
                            onClick = {
                                if (!isServerUrlValid) {
                                    Toast.makeText(context, "Harap isi URL Server Ujian terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val expiresAt = System.currentTimeMillis() + (expiryHours * 3600L * 1000L)
                                onSaveQr(
                                    examName,
                                    examCode,
                                    serverUrl,
                                    schoolName,
                                    finalPayload,
                                    isEncryptionEnabled,
                                    sessionPin,
                                    expiresAt
                                )
                                Toast.makeText(context, "QR Code '$examName' berhasil disimpan ke daftar aktif!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = isServerUrlValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleekSuccess,
                                disabledContainerColor = SleekBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Copy Payload Button
                    Button(
                        onClick = {
                            if (!isServerUrlValid || finalPayload.isBlank()) {
                                Toast.makeText(context, "Harap isi URL Server Ujian terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            clipboardManager.setText(AnnotatedString(finalPayload))
                            Toast.makeText(context, "Muatan QR berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isServerUrlValid && finalPayload.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SleekSurfaceVariant,
                            disabledContainerColor = SleekBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, SleekBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = SleekTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Kode", color = SleekTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Comparison Breakdown Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SleekBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Perbandingan Hasil Scan:",
                    color = SleekNavyHeader,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("📱 ", fontSize = 13.sp)
                    Column {
                        Text(
                            text = "Kamera Biasa / Google Lens / Browser Luar:",
                            color = SleekTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = if (isEncryptionEnabled)
                                "Hanya melihat teks acak '${finalPayload.take(28)}...' dan TIDAK BISA membuka link server."
                            else
                                "Melihat URL langsung dan bisa dibuka di Google Chrome biasa.",
                            color = if (isEncryptionEnabled) SleekSuccess else SleekTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("🛡️ ", fontSize = 13.sp)
                    Column {
                        Text(
                            text = "Aplikasi Exam Browser Ini:",
                            color = SleekTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "Otomatis mendekripsi data secara internal, memvalidasi integritas, dan langsung mengunci perangkat ke mode ujian CBT.",
                            color = SleekTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab Tampilan Daftar QR Code yang Masih Aktif.
 * Jika QR sudah melewati waktu kedaluwarsa, otomatis hilang dari daftar ini.
 */
@Composable
private fun SavedQrsTabContent(
    activeSavedQrs: List<SavedQrEntity>,
    onRefresh: () -> Unit,
    onDelete: (Long) -> Unit,
    onViewDetail: (SavedQrEntity) -> Unit,
    onPrintQr: (QrPrintData) -> Unit = {},
    onGoToGenerator: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Bar info atas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daftar QR Code Aktif",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SleekNavyHeader
                )
                Text(
                    text = "QR otomatis terhapus dari daftar setelah kedaluwarsa",
                    fontSize = 11.5.sp,
                    color = SleekTextSecondary
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Segarkan",
                    tint = SleekPrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeSavedQrs.isEmpty()) {
            // Tampilan kosong jika belum ada QR aktif tersimpan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SleekSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .border(1.dp, SleekBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(SleekPrimaryBlue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = SleekPrimaryBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Belum Ada QR Aktif Tersimpan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SleekNavyHeader
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Buat QR Code di tab 'Buat QR Ujian' lalu klik tombol 'Simpan QR' agar siswa dapat memindainya langsung dari daftar ini.",
                            fontSize = 12.sp,
                            color = SleekTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onGoToGenerator,
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Generator QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // List item QR aktif
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeSavedQrs, key = { it.id }) { qr ->
                    val now = System.currentTimeMillis()
                    val remainingMs = qr.expiresAt - now
                    val remainingHours = (remainingMs / (1000 * 60 * 60)).coerceAtLeast(0)
                    val remainingMins = ((remainingMs / (1000 * 60)) % 60).coerceAtLeast(0)
                    val expireDateStr = sdf.format(Date(qr.expiresAt))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SleekSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SleekBorder, RoundedCornerShape(16.dp))
                            .clickable { onViewDetail(qr) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (qr.isEncrypted) SleekSuccessBg else SleekSecondaryBlueContainer,
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (qr.isEncrypted) Icons.Default.Lock else Icons.Default.QrCode,
                                            contentDescription = null,
                                            tint = if (qr.isEncrypted) SleekSuccess else SleekPrimaryBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = qr.examName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = SleekTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Kode: ${qr.examCode}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = SleekPrimaryBlue
                                        )
                                    }
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        onDelete(qr.id)
                                        Toast.makeText(context, "QR Code dihapus dari daftar", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus QR",
                                        tint = SleekTextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Server URL snippet
                            Text(
                                text = "🌐 ${qr.serverUrl}",
                                fontSize = 11.5.sp,
                                color = SleekTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (qr.schoolName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🏫 ${qr.schoolName}",
                                    fontSize = 11.sp,
                                    color = SleekTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom Bar: Sisa waktu aktif + Tombol Tampilkan / Salin
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status sisa waktu aktif
                                Surface(
                                    color = SleekSuccessBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekSuccess.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = SleekSuccess,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Sisa ~$remainingHours jam $remainingMins mnt (s.d. $expireDateStr)",
                                            fontSize = 10.5.sp,
                                            color = SleekSuccess,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Tombol Cetak Dokumen PDF
                                    IconButton(
                                        onClick = {
                                            onPrintQr(QrPrintData.fromSavedQr(qr))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "Cetak PDF",
                                            tint = SleekPrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(qr.qrPayload))
                                            Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin",
                                            tint = SleekPrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Button(
                                        onClick = { onViewDetail(qr) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Lihat QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = SleekTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SleekTextPrimary,
                unfocusedTextColor = SleekTextPrimary,
                focusedBorderColor = SleekPrimaryBlue,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = SleekInputBg,
                unfocusedContainerColor = SleekInputBg
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
