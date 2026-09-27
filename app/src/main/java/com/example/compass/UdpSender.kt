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
import java.net.NetworkInterface
import java.util.Collections

class UdpSender(private val context: Context, private val targetPort: Int) {
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    private var broadcastAddress: InetAddress? = null
    private var lastInterfaceCheck = 0L
    private val INTERFACE_CHECK_INTERVAL_MS = 3000L


    fun start(getData: () -> String) {
        job = CoroutineScope(Dispatchers.IO).launch {

            socket = DatagramSocket()
            socket?.broadcast = true

            refreshBroadcastAddress()

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    if (broadcastAddress == null || now - lastInterfaceCheck > INTERFACE_CHECK_INTERVAL_MS) {
                        refreshBroadcastAddress()
                    }

                    val addr = broadcastAddress
                    if (addr != null) {
                        val bytes = getData().toByteArray()
                        val packet = DatagramPacket(bytes, bytes.size, addr, targetPort)
                        socket?.send(packet)
                    }

                } catch (e: java.net.SocketException) {
                    Log.w("UdpSender", "Closed socket, sender stopped: ${e.message}")
                } catch (e: Exception) {
                    Log.e("UdpSender", "Error sending: ${e.message}")
                }
                delay(33)
            }
        }
    }

    private fun refreshBroadcastAddress() {
        lastInterfaceCheck = System.currentTimeMillis()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                if (!networkInterface.displayName.contains("swlan")) continue

                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        if (broadcastAddress == null || broadcastAddress?.hostAddress != broadcast.hostAddress) {
                            Log.d("UdpSender", "Broadcast actualizado: ${broadcast.hostAddress}")
                        }
                        broadcastAddress = broadcast
                        return
                    }
                }
            }

            if (broadcastAddress == null) {
                Log.w(
                    "UdpSender",
                    "swlan0 aún no disponible, reintentando en ${INTERFACE_CHECK_INTERVAL_MS}ms"
                )
            }

        } catch (e: Exception) {
            Log.e("UdpSender", "Error obteniendo broadcast: ${e.message}")
        }
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        socket = null
    }
}