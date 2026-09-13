package com.example.compass

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections

class UdpSender(private val context: Context, private val targetPort: Int) {
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    fun start(getData: () -> String) {
        job = CoroutineScope(Dispatchers.IO).launch {
            val networkInfo = getHotspotNetworkInfo()

            if (networkInfo == null) {
                Log.e("UdpSender", "No se encontró la interfaz del hotspot")
                return@launch
            }

            val (localAddress, broadcastAddress) = networkInfo
            Log.d(
                "UdpSender",
                "Interfaz hotspot: local=${localAddress.hostAddress}, broadcast=${broadcastAddress.hostAddress}"
            )

            socket = DatagramSocket(null)
            socket?.reuseAddress = true
            socket?.bind(InetSocketAddress(localAddress, 0))   // ← FORZAR la interfaz de salida
            socket?.broadcast = true

            while (isActive) {
                try {
                    val bytes = getData().toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddress, targetPort)
                    socket?.send(packet)
                } catch (e: java.net.SocketException) {
                    Log.w("UdpSender", "Closed socket, sender stopped: ${e.message}")
                } catch (e: Exception) {
                    Log.e("UdpSender", "Error sending: ${e.message}")
                }
                delay(33)
            }
        }
    }

    // Devuelve (IP propia de la interfaz, broadcast de la interfaz)
    private fun getHotspotNetworkInfo(): Pair<InetAddress, InetAddress>? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Prioridad: buscar la interfaz del hotspot
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                if (!networkInterface.displayName.contains("swlan")) continue

                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    val local = interfaceAddress.address
                    if (broadcast != null && local != null) {
                        Log.d(
                            "UdpSender",
                            "swlan encontrada: local=${local.hostAddress}, broadcast=${broadcast.hostAddress}, prefix=${interfaceAddress.networkPrefixLength}"
                        )
                        return Pair(local, broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UdpSender", "Error obteniendo info de red: ${e.message}")
        }
        return null
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        socket = null
    }
}