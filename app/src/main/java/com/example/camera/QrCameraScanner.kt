package com.example.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.SleekNavyHeader
import com.example.ui.theme.SleekPrimaryBlue
import com.example.ui.theme.SleekSurface
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrCameraScannerView(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val qrText = decodeQrFromImageUri(context, uri)
            if (qrText != null) {
                triggerHapticFeedback(context)
                onQrCodeScanned(qrText)
            } else {
                Toast.makeText(context, "Tidak ada QR Code yang terdeteksi pada gambar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Laser Animation Transition
    val infiniteTransition = rememberInfiniteTransition(label = "laser_animation")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(
                onQrCodeScanned = { result ->
                    triggerHapticFeedback(context)
                    onQrCodeScanned(result)
                },
                onCameraReady = { control ->
                    cameraControl = control
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Reticle & Animated Laser Bar
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxBoxDimension = minOf(size.width * 0.72f, size.height * 0.45f)
                val boxSize = minOf(270.dp.toPx(), maxBoxDimension).coerceAtLeast(180.dp.toPx())
                val left = (size.width - boxSize) / 2
                val top = (size.height - boxSize) / 2.3f

                // Semi-transparent darkened background around scanner box
                drawRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    size = size
                )

                // Corner Reticle (Cyan / Sky Blue)
                val cornerLength = 32.dp.toPx()
                val cornerStroke = 4.5.dp.toPx()
                val cornerRadius = 14.dp.toPx()
                val reticleColor = Color(0xFF38BDF8)

                // Outer border box
                drawRoundRect(
                    color = reticleColor.copy(alpha = 0.4f),
                    topLeft = Offset(left, top),
                    size = Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Draw 4 distinct sharp corners
                // Top-Left
                drawLine(reticleColor, Offset(left, top + cornerLength), Offset(left, top + cornerRadius), cornerStroke)
                drawLine(reticleColor, Offset(left + cornerRadius, top), Offset(left + cornerLength, top), cornerStroke)

                // Top-Right
                drawLine(reticleColor, Offset(left + boxSize - cornerLength, top), Offset(left + boxSize - cornerRadius, top), cornerStroke)
                drawLine(reticleColor, Offset(left + boxSize, top + cornerRadius), Offset(left + boxSize, top + cornerLength), cornerStroke)

                // Bottom-Left
                drawLine(reticleColor, Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize - cornerRadius), cornerStroke)
                drawLine(reticleColor, Offset(left + cornerRadius, top + boxSize), Offset(left + cornerLength, top + boxSize), cornerStroke)

                // Bottom-Right
                drawLine(reticleColor, Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize - cornerRadius, top + boxSize), cornerStroke)
                drawLine(reticleColor, Offset(left + boxSize, top + boxSize - cornerLength), Offset(left + boxSize, top + boxSize - cornerRadius), cornerStroke)

                // Animated Laser Scan Line with glow
                val laserY = top + (boxSize * laserProgress)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF38BDF8).copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.95f),
                            Color(0xFF38BDF8).copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        startY = laserY - 8.dp.toPx(),
                        endY = laserY + 8.dp.toPx()
                    ),
                    topLeft = Offset(left + 8.dp.toPx(), laserY - 4.dp.toPx()),
                    size = Size(boxSize - 16.dp.toPx(), 8.dp.toPx())
                )
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Scanner",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Pindai QR Code",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 0.3.sp
                )

                // Flashlight / Torch Toggle
                IconButton(
                    onClick = {
                        isTorchEnabled = !isTorchEnabled
                        cameraControl?.enableTorch(isTorchEnabled)
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isTorchEnabled) "Matikan Flash" else "Nyalakan Flash",
                        tint = if (isTorchEnabled) Color(0xFFFBBF24) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom Actions & Guidance Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Arahkan kamera ke QR Code Server / Soal",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Gallery Picker Button
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih Gambar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Manual Code Input Button
                            OutlinedButton(
                                onClick = { showManualInputDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Input Manual", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            // Permission missing fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Izin Kamera Diperlukan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (cameraPermissionState.status.shouldShowRationale) {
                        "Aplikasi membutuhkan izin kamera untuk memindai QR Code server ujian online (CBT, LMS, atau Web Ujian)."
                    } else {
                        "Silakan berikan izin akses kamera untuk menggunakan pemindai QR Code."
                    },
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Berikan Izin Kamera")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Kembali")
                }
            }
        }
    }

    // Manual Input Dialog
    if (showManualInputDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = {
                Text(
                    text = "Input URL / Kode Manual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Tempel atau masukkan link/kode server ujian di bawah ini:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        placeholder = { Text("https://ujian.sekolah.sch.id") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                manualInputText = clipText.trim()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Tempel dari Clipboard", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualInputText.isNotBlank()) {
                            showManualInputDialog = false
                            keyboardController?.hide()
                            onQrCodeScanned(manualInputText.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryBlue)
                ) {
                    Text("Gunakan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    onCameraReady: (CameraControl) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val isScanned = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (e: Exception) {
                Log.e("QrScanner", "Error unbinding camera on dispose", e)
            }
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isScanned.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val qrResult = processImageProxyRobust(imageProxy)
                        if (qrResult != null && isScanned.compareAndSet(false, true)) {
                            ContextCompat.getMainExecutor(ctx).execute {
                                try {
                                    cameraProvider.unbindAll()
                                } catch (e: Exception) {
                                    Log.e("QrScanner", "Error unbinding camera", e)
                                }
                                onQrCodeScanned(qrResult)
                            }
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera.cameraControl)
                } catch (e: Exception) {
                    Log.e("QrScanner", "Camera setup/binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

/**
 * Robust Multi-Format & Multi-Orientation Barcode/QR Decoder
 * Handles CameraX YUV_420_888 row padding / rowStride correctly
 */
private fun processImageProxyRobust(imageProxy: ImageProxy): String? {
    if (imageProxy.format != ImageFormat.YUV_420_888) {
        return null
    }

    val plane = imageProxy.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = imageProxy.width
    val height = imageProxy.height
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

    // Extract packed luminance (Y) data taking into account rowStride and pixelStride
    val yData = ByteArray(width * height)
    buffer.rewind()
    if (rowStride == width && pixelStride == 1) {
        buffer.get(yData, 0, width * height)
    } else {
        for (row in 0 until height) {
            val rowOffset = row * rowStride
            if (pixelStride == 1) {
                buffer.position(rowOffset)
                buffer.get(yData, row * width, width)
            } else {
                for (col in 0 until width) {
                    yData[row * width + col] = buffer.get(rowOffset + col * pixelStride)
                }
            }
        }
    }

    // 1. Prepare rotated Y data matching camera preview orientation
    val (rotatedData, rotWidth, rotHeight) = rotateYUV(yData, width, height, rotationDegrees)

    // Primary reader hints: QR_CODE first for instant recognition
    val qrHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )
    val reader = MultiFormatReader().apply { setHints(qrHints) }

    // Source aligned with preview orientation
    val rotatedSource = PlanarYUVLuminanceSource(
        rotatedData, rotWidth, rotHeight, 0, 0, rotWidth, rotHeight, false
    )

    // Attempt 1: HybridBinarizer on rotated source (standard QR)
    try {
        val bitmap = BinaryBitmap(HybridBinarizer(rotatedSource))
        val result = reader.decodeWithState(bitmap)
        if (result?.text != null) return result.text
    } catch (_: Exception) {}

    // Attempt 2: GlobalHistogramBinarizer on rotated source (uneven lighting)
    try {
        val bitmap = BinaryBitmap(GlobalHistogramBinarizer(rotatedSource))
        val result = reader.decodeWithState(bitmap)
        if (result?.text != null) return result.text
    } catch (_: Exception) {}

    // Attempt 3: Inverted HybridBinarizer (dark-mode / inverted QR codes)
    try {
        val bitmap = BinaryBitmap(HybridBinarizer(rotatedSource.invert()))
        val result = reader.decodeWithState(bitmap)
        if (result?.text != null) return result.text
    } catch (_: Exception) {}

    // Attempt 4: Unrotated raw sensor orientation fallback
    if (rotationDegrees != 0) {
        try {
            val rawSource = PlanarYUVLuminanceSource(
                yData, width, height, 0, 0, width, height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(rawSource))
            val result = reader.decodeWithState(bitmap)
            if (result?.text != null) return result.text
        } catch (_: Exception) {}
    } else {
        // If rotation was 0, also try 90-degree rotated source (sensor orientation quirk)
        try {
            val (altRotated, altW, altH) = rotateYUV(yData, width, height, 90)
            val altSource = PlanarYUVLuminanceSource(
                altRotated, altW, altH, 0, 0, altW, altH, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(altSource))
            val result = reader.decodeWithState(bitmap)
            if (result?.text != null) return result.text
        } catch (_: Exception) {}
    }

    // Attempt 5: Multi-format fallback (Data Matrix, Aztec, PDF-417, Code 128)
    val multiHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39
        ),
        DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )
    val multiReader = MultiFormatReader().apply { setHints(multiHints) }
    try {
        val bitmap = BinaryBitmap(HybridBinarizer(rotatedSource))
        val result = multiReader.decodeWithState(bitmap)
        if (result?.text != null) return result.text
    } catch (_: Exception) {}

    return null
}

/**
 * Fast rotation of tightly packed YUV luminance plane
 */
private data class RotatedBuffer(val data: ByteArray, val width: Int, val height: Int)

private fun rotateYUV(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): RotatedBuffer {
    return when (rotationDegrees) {
        90 -> {
            val rotated = ByteArray(width * height)
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rotated[i++] = data[y * width + x]
                }
            }
            RotatedBuffer(rotated, height, width)
        }
        180 -> {
            val rotated = ByteArray(width * height)
            val count = width * height
            for (i in 0 until count) {
                rotated[i] = data[count - 1 - i]
            }
            RotatedBuffer(rotated, width, height)
        }
        270 -> {
            val rotated = ByteArray(width * height)
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rotated[i++] = data[y * width + x]
                }
            }
            RotatedBuffer(rotated, height, width)
        }
        else -> RotatedBuffer(data, width, height)
    }
}

/**
 * Decode QR Code from Image URI (Gallery screenshot/photo)
 * Safely downsamples large photos and handles inverted / alternate binarization
 */
private fun decodeQrFromImageUri(context: Context, uri: Uri): String? {
    return try {
        // First determine image bounds to avoid OutOfMemoryError
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // Downsample if image is large (limit to ~1600px max dimension)
        var sampleSize = 1
        val maxDim = maxOf(origWidth, origHeight)
        while (maxDim / sampleSize > 1600) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39
            ),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val reader = MultiFormatReader().apply { setHints(hints) }

        // Attempt 1: HybridBinarizer
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val text = reader.decodeWithState(binaryBitmap)?.text
            if (text != null) return text
        } catch (_: Exception) {}

        // Attempt 2: GlobalHistogramBinarizer
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val text = reader.decodeWithState(binaryBitmap)?.text
            if (text != null) return text
        } catch (_: Exception) {}

        // Attempt 3: Inverted HybridBinarizer (dark-mode / inverted colors)
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
            val text = reader.decodeWithState(binaryBitmap)?.text
            if (text != null) return text
        } catch (_: Exception) {}

        null
    } catch (e: Exception) {
        Log.e("QrScanner", "Gallery QR decode error", e)
        null
    }
}

/**
 * Trigger subtle haptic vibration upon scanning
 */
private fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70L)
            }
        }
    } catch (_: Exception) {}
}
