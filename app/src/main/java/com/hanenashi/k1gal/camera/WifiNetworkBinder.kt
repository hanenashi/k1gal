package com.hanenashi.k1gal.camera

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

internal class WifiNetworkBinder(private val connectivity: ConnectivityManager) {
    fun bindProcessToWifi() {
        val wifiNetwork: Network = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return
        connectivity.bindProcessToNetwork(wifiNetwork)
    }

    fun wifiLocalIps(): List<String> {
        return connectivity.allNetworks
            .filter { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
            }
            .flatMap { network ->
                connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
                    .mapNotNull { linkAddress ->
                        val address = linkAddress.address
                        if (address is Inet4Address && !address.isLoopbackAddress) address.hostAddress else null
                    }
            }
            .filterNot { it.startsWith("100.") }
            .distinct()
    }
}
