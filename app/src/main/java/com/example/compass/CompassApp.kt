package com.example.compass;

import android.content.Context


object CompassApp {

    lateinit var sensorController: SensorController
        private set
    lateinit var udpSender: UdpSender
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        sensorController = SensorController(appContext)
        udpSender = UdpSender(appContext, targetPort = 9000)
        initialized = true
    }
}