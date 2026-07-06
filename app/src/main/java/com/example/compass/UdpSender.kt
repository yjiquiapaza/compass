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

class UdpSender(private val targetIp: String, private val targetPort: Int) {
    private var socket: DatagramSocket? = null
    private var job: Job? = null

    fun start(getData: () -> String) {
        job = CoroutineScope(Dispatchers.IO).launch {
            socket = DatagramSocket()
            val address = InetAddress.getByName(targetIp)
            while (isActive){
                try {
                    val bytes = getData().toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, address, targetPort)
                    socket?.send(packet)
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