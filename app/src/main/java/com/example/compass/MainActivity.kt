package com.example.compass

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Build
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CompassApp.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        ContextCompat.startForegroundService(this, Intent(this, CompassService::class.java))

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorDisplay(CompassApp.sensorController, CompassApp.udpSender)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getDeclination(context: Context, controller: SensorController, force: Boolean = false) {
    if (!force && controller.latitud != 0.0 || controller.longitud != 0.0) return
    controller.updateLocation = true
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
            controller.updateLocation = false
        } else {
            Log.e("Location", "getCurrentLocation get null values...")
            Handler(Looper.getMainLooper()).postDelayed({
                getDeclination(context, controller, force)
            }, 3000)
        }
    }.addOnFailureListener { e ->
        Log.e("Location", "Error in get Location ${e.message}")
        controller.updateLocation = false
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        ExpandedSection(title = "Magnetic Field", expandedByDefault = true) {
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

        Spacer(modifier = Modifier.height(4.dp))

        ExpandedSection(title = "Magnetic Bearing", expandedByDefault = true) {
            Text(
                text = "%.0f".format(controller.heading),
                style = MaterialTheme.typography.displayLarge
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        /*
        ExpandedSection(title = "IP (Quest 3)", expandedByDefault = true) {

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
                Text("Update IP")
            }

            if (ipSaved.isBlank()) {
                Text(
                    "⚠️ IP without configuration, nothing is being sent",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("Sending to: $ipSaved", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

         */
        ExpandedSection(title = "Current Location (GPS)") {
            Location(controller, context)
        }
    }
}

@Composable
fun Location(controller: SensorController, context: Context) {
    Column {
        if (controller.latitud == 0.0 && controller.longitud == 0.0) {
            Text(
                "Getting current Location ...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Lat: %.6f".format(java.util.Locale.US, controller.latitud))
            Text("Lon: %.6f".format(java.util.Locale.US, controller.longitud))
        }
        Button(
            onClick = { getDeclination(context, controller, force = true) },
            enabled = !controller.updateLocation
        ) {
            if (controller.updateLocation) {
                Text("Updating ...")
            } else {
                Text("Update Location")
            }
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
                .padding(vertical = 4.dp),
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
}