package com.example.compass

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : ComponentActivity() {

    private lateinit var sensorController: SensorController
    private lateinit var udpSender: UdpSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorController = SensorController(this)
        udpSender = UdpSender(targetPort = 9000)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorDisplay(sensorController, udpSender)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorController.start()
    }

    override fun onPause() {
        super.onPause()
        sensorController.stop()
        udpSender.stop()
    }
}

@SuppressLint("MissingPermission")
private fun getDeclination(context: Context, controller: SensorController) {
    if (controller.latitud != 0.0 || controller.longitud != 0.0) return
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    fusedClient.getCurrentLocation(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        CancellationTokenSource().token
    ).addOnSuccessListener { location ->
        if (location != null) {
            controller.latitud = location.latitude
            controller.longitud = location.longitude

            var geoField =
                GeomagneticField(
                    location.latitude.toFloat(),
                    location.longitude.toFloat(),
                    location.altitude.toFloat(),
                    System.currentTimeMillis()
                )
            controller.decline = geoField.declination
        } else {
            Log.e("Location", "getCurrentLocation get null values...")
            Handler(Looper.getMainLooper()).postDelayed({
                getDeclination(context, controller)
            }, 3000)
        }
    }
}

@Composable
fun SensorDisplay(controller: SensorController, udpSender: UdpSender) {

    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { approve ->
            if (approve) {
                getDeclination(context, controller)
            }
        }

    LaunchedEffect(Unit) {
        val isApprove = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isApprove) {
            getDeclination(context, controller)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    var ipText by remember { mutableStateOf(IpPreferences.getSavedIp(context)) }
    var ipSaved by remember { mutableStateOf(ipText) }
    var messageError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        udpSender.start(
            getIp = { ipSaved },
            getData = {
                String.format(
                    java.util.Locale.US,
                    """{"heading":%.2f,"lat":%.6f,"lon":%.6f}""".format(
                        controller.heading,
                        controller.latitud,
                        controller.longitud
                    )
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        ExpandedSection(title = "Magnetic Field", expandedByDefault = true) {
            Text(
                text = "Total Magnetic Field", style = MaterialTheme.typography.titleMedium
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
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Magnetic Bearing", style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "%.0f".format(controller.heading),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Destination IP (Quest 3) ", style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = ipText,
            onValueChange = {
                ipText = it
                messageError = null
            },
            label = { Text("Example: 192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = messageError != null
        )

        Button(onClick = {
            if (isIpValid(ipText)) {
                IpPreferences.saveIp(context, ipText)
                ipSaved = ipText
                messageError = null
            } else {
                messageError = "IP invalid, Format: 192.168.1.50"
            }
        }) {
            Text("Save")
        }

        if (ipSaved.isBlank()) {
            Text(
                "⚠️ IP without configuration, nothing is being sent",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text("Sending to: $ipSaved", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Location(controller.latitud, controller.longitud)
    }
}

@Composable
fun Location(latitud: Double, longitud: Double) {
    Column {
        Text("Ubicación (GPS)", style = MaterialTheme.typography.titleSmall)

        if (latitud == 0.0 && longitud == 0.0) {
            Text(
                "Obteniendo ubicación...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Lat: %.6f".format(java.util.Locale.US, latitud))
            Text("Lon: %.6f".format(java.util.Locale.US, longitud))
        }
    }
}

@Composable
fun ExpandedSection(
    title: String,
    expandedByDefault: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Transparent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleMedium,
            )

        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
}