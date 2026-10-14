package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class BatteryInfo(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val isLow: Boolean = false,
    val isCritical: Boolean = false
)

fun getInitialBatteryInfo(context: Context): BatteryInfo {
    return try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            100
        }

        BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            isLow = percentage <= 15 && !isCharging,
            isCritical = percentage <= 10 && !isCharging
        )
    } catch (_: Exception) {
        BatteryInfo()
    }
}

@Composable
fun rememberBatteryState(): State<BatteryInfo> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(getInitialBatteryInfo(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val percentage = if (level >= 0 && scale > 0) {
                        (level * 100) / scale
                    } else {
                        100
                    }

                    batteryState.value = BatteryInfo(
                        percentage = percentage,
                        isCharging = isCharging,
                        isLow = percentage <= 15 && !isCharging,
                        isCritical = percentage <= 10 && !isCharging
                    )
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    return batteryState
}
