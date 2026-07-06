package com.example.compass

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var sensorController: SensorController
    private lateinit var udpSender: UdpSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorController = SensorController(this)
        udpSender = UdpSender(targetIp = "10.14.112.141", targetPort = 9000)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorDisplay(sensorController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorController.start()
        udpSender.start {
            """{"heading":%.2f}""".format(sensorController.heading)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorController.stop()
        udpSender.stop()
    }
}


@Composable
fun SensorDisplay(controller: SensorController) {

    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Total Magnetic Field",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "%.1f µT".format(controller.magneticField),
            style = MaterialTheme.typography.displayMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text("Raw Axis: ", style = MaterialTheme.typography.titleSmall)
        Text("X: %.1f µT".format(controller.magX))
        Text("Y: %.1f µT".format(controller.magY))
        Text("Z: %.1f µT".format(controller.magZ))

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Magnetic Bearing",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "%.0f".format(controller.heading),
            style = MaterialTheme.typography.displayLarge
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
}