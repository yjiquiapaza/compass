package com.example.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class SensorController(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)


    var magneticField by mutableStateOf(0f)
        private set

    var magX by mutableStateOf(0f)
        private set

    var magY by mutableStateOf(0f)
        private set

    var magZ by mutableStateOf(0f)
        private set

    // Final heading in grades (0 = north, 90 = East, 180 = South, 270 = West)
    var heading by mutableStateOf(0f)
        private set

    var decline = 0f

    fun start() {

        if (accelerometer == null && magnetometer == null) {
            Log.e("SensorController", "This device doesn't have these sensors")
            return
        }

        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )

        sensorManager.registerListener(
            this,
            magnetometer,
            SensorManager.SENSOR_DELAY_UI
        )

    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d("SensorController", "Sensor is disconnected")
    }

    private fun updateHeading() {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimutGrades = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            heading = (azimutGrades + decline + 360) % 360
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("SensorController", "${sensor?.name} accuracy: ${accuracy}")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                magX = x
                magY = y
                magZ = z

                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                magneticField = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                Log.d("SensorController", "Total magnetic field: %.1f µT".format(magnitude))
            }
        }
        updateHeading()
    }
}