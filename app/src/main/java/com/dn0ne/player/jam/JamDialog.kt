package com.dn0ne.player.jam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun JamDialog(
    jamManager: JamManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val currentRole by jamManager.jamRole.collectAsState()
    val discoveredHosts by jamManager.discoveredHosts.collectAsState()
    val connectedPeers by jamManager.connectedPeers.collectAsState()

    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            // Permissions granted
        }
    }

    fun checkAndRun(action: () -> Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            action()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(imageVector = Icons.Rounded.Group, contentDescription = "Jam Session")
        },
        title = {
            Text(text = "Local Jam Session")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (currentRole) {
                    JamRole.NONE -> {
                        Text(
                            text = "Listen together with nearby friends without using mobile data.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                checkAndRun {
                                    val deviceName = Build.MODEL ?: "Panchajanya Host"
                                    jamManager.startHosting(deviceName)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Radio, contentDescription = null)
                            Text(text = "Start Jam (Host)", modifier = Modifier.padding(start = 8.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                checkAndRun {
                                    jamManager.startDiscovering()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                            Text(text = "Join Jam (Friend)", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    JamRole.HOST -> {
                        Text(
                            text = "Hosting Jam as: ${Build.MODEL}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Connected Friends: ${connectedPeers.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Text("Broadcasting session...", style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = { jamManager.stopJam() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Jam")
                        }
                    }

                    JamRole.GUEST -> {
                        Text(
                            text = "Searching for nearby Jams...",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (connectedPeers.isNotEmpty()) {
                            Text(
                                text = "Connected to Jam session!",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall
                            )
                        } else if (discoveredHosts.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Text("Looking for hosts...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(discoveredHosts) { host ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val guestName = Build.MODEL ?: "Panchajanya Guest"
                                                jamManager.joinHost(host.endpointId, guestName)
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(text = host.endpointName, style = MaterialTheme.typography.titleSmall)
                                            Text(text = "Tap to connect", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { jamManager.stopJam() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect / Cancel")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}
