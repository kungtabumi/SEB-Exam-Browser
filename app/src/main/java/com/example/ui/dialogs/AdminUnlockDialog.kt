package com.example.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SleekBorder
import com.example.ui.theme.SleekError
import com.example.ui.theme.SleekErrorBg
import com.example.ui.theme.SleekInputBg
import com.example.ui.theme.SleekNavyHeader
import com.example.ui.theme.SleekPrimaryBlue
import com.example.ui.theme.SleekSecondaryBlueContainer
import com.example.ui.theme.SleekSurface
import com.example.ui.theme.SleekTextMuted
import com.example.ui.theme.SleekTextPrimary
import com.example.ui.theme.SleekTextSecondary

enum class AdminAuthMode {
    ADMIN_MENU,
    EXIT_EXAM
}

@Composable
fun AdminUnlockDialog(
    mode: AdminAuthMode = AdminAuthMode.EXIT_EXAM,
    onVerifyPin: (String) -> Boolean,
    onOpenQrScan: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val isModeAdminMenu = mode == AdminAuthMode.ADMIN_MENU

    val submitVerification = {
        if (pinInput.isBlank()) {
            errorMsg = if (isModeAdminMenu) {
                "Masukkan PIN Mode Admin!"
            } else {
                "Masukkan PIN Keluar Ujian!"
            }
        } else {
            val success = onVerifyPin(pinInput)
            if (!success) {
                errorMsg = if (isModeAdminMenu) {
                    "PIN Mode Admin Tidak Sesuai!"
                } else {
                    "PIN Keluar Ujian Tidak Sesuai!"
                }
            } else {
                val successMessage = if (isModeAdminMenu) {
                    "Akses Admin Diberikan!"
                } else {
                    "Task Lock Terbuka, Sesi Ujian Berakhir!"
                }
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .border(1.dp, SleekBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Surface(
                    color = if (isModeAdminMenu) SleekSecondaryBlueContainer else SleekErrorBg,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isModeAdminMenu) Icons.Default.Settings else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isModeAdminMenu) SleekPrimaryBlue else SleekError,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Badge / Tag
                Surface(
                    color = if (isModeAdminMenu) SleekSecondaryBlueContainer else SleekErrorBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isModeAdminMenu) "MODE ADMINISTRATOR" else "KIOSK LOCK AKTIF",
                        color = if (isModeAdminMenu) SleekPrimaryBlue else SleekError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                Text(
                    text = if (isModeAdminMenu) "AUTENTIKASI MODE ADMIN" else "KONFIRMASI KELUAR UJIAN",
                    color = SleekNavyHeader,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                // Explanation / Keterangan (clearly differentiated)
                Text(
                    text = if (isModeAdminMenu) {
                        "Masukkan PIN Admin untuk membuka Menu Admin dan Generator QR Ujian."
                    } else {
                        "Masukkan PIN Keluar untuk mengakhiri sesi ujian dan membuka kunci sematan."
                    },
                    color = SleekTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )

                // Input Field
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorMsg = null
                    },
                    placeholder = {
                        Text(
                            text = if (isModeAdminMenu) "PIN Admin" else "PIN Keluar Ujian",
                            color = SleekTextMuted,
                            fontSize = 13.sp
                        )
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Sembunyikan Password" else "Tampilkan Password",
                                tint = SleekTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextPrimary,
                        unfocusedTextColor = SleekTextPrimary,
                        focusedBorderColor = if (isModeAdminMenu) SleekPrimaryBlue else SleekError,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SleekInputBg,
                        unfocusedContainerColor = SleekInputBg
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitVerification() }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Surface(
                        color = SleekErrorBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = errorMsg ?: "",
                            color = SleekError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Action Button
                Button(
                    onClick = { submitVerification() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isModeAdminMenu) SleekPrimaryBlue else SleekError
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isModeAdminMenu) Icons.Default.Settings else Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isModeAdminMenu) "Buka Menu Admin" else "Buka Kunci & Keluar Ujian",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel Button
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SleekBorder),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isModeAdminMenu) "Batal" else "Batal (Tetap di Ujian)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
