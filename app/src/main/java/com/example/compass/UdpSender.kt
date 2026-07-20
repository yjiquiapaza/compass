package com.example.compass

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

class UdpSender(private val targetPort: Int) {
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    private var currentIp: String = ""
    private var cachedAddress: InetAddress? = null

    fun start(getIp: () -> String, getData: () -> String) {
        job = CoroutineScope(Dispatchers.IO).launch {
            socket = DatagramSocket()
            while (isActive){
                try {

                    val ip = getIp()

                    if (ip.isNotBlank()) {
                        if (ip != currentIp) {
                            currentIp = ip
                            cachedAddress = InetAddress.getByName(ip)
                            Log.d("UpSender", "Update IP $ip")
                        }

                        val bytes = getData().toByteArray()
                        val packet = DatagramPacket(bytes, bytes.size, cachedAddress, targetPort)

                        socket?.send(packet)
                    }
                } catch (e: java.net.SocketException){
                    Log.w("UpSender", "Closed socket, sender is stoper: ${e.message}")

                } catch (e: Exception) {
                    Log.e("UdpSender", "Error sending: ${e.message}")
                }
                delay(33)
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        socket = null
    }
}