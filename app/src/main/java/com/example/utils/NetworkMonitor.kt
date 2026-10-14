package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    NONE
}

data class NetworkConnectionState(
    val isConnected: Boolean = true,
    val isWifi: Boolean = true,
    val connectionType: ConnectionType = ConnectionType.WIFI,
    val isInternetValidated: Boolean = true
)

fun getInitialNetworkState(context: Context): NetworkConnectionState {
    return try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkConnectionState()
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkConnectionState(
            isConnected = false,
            isWifi = false,
            connectionType = ConnectionType.NONE,
            isInternetValidated = false
        )
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkConnectionState(
            isConnected = false,
            isWifi = false,
            connectionType = ConnectionType.NONE,
            isInternetValidated = false
        )

        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val type = when {
            isWifi -> ConnectionType.WIFI
            isCellular -> ConnectionType.CELLULAR
            isEthernet -> ConnectionType.ETHERNET
            isVpn -> ConnectionType.VPN
            else -> ConnectionType.NONE
        }

        NetworkConnectionState(
            isConnected = isConnected,
            isWifi = isWifi,
            connectionType = type,
            isInternetValidated = isValidated
        )
    } catch (_: Exception) {
        NetworkConnectionState()
    }
}

@Composable
fun rememberNetworkConnectivityState(): State<NetworkConnectionState> {
    val context = LocalContext.current
    val networkState = remember { mutableStateOf(getInitialNetworkState(context)) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        if (connectivityManager == null) {
            return@DisposableEffect onDispose {}
        }

        val updateState = {
            networkState.value = getInitialNetworkState(context)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateState()
            }

            override fun onLost(network: Network) {
                updateState()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateState()
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            updateState()
        }

        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    return networkState
}
