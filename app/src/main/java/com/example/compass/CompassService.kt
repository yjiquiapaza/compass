package com.example.compass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat


class CompassService : Service() {

    override fun onCreate() {
        super.onCreate()
        CompassApp.init(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        CompassApp.sensorController.start()
        CompassApp.udpSender.start {
            String.format(
                java.util.Locale.US,
                """{"heading":%.2f,"lat":%.6f,"lon":%.6f}""",
                CompassApp.sensorController.heading,
                CompassApp.sensorController.latitud,
                CompassApp.sensorController.longitud
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        CompassApp.sensorController.stop()
        CompassApp.udpSender.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compass Activated")
            .setContentText("Send the bearing in background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Compass in background",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "compass_channel"
        const val NOTIFICATION_ID = 1
    }
}