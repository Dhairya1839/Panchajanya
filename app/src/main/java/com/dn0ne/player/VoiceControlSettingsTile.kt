package com.dnone.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceControlSettingsTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(false) }

    // Sync state on load
    LaunchedEffect(Unit) {
        isEnabled = NativeVoiceService.isRunning(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        if (recordAudioGranted && notificationGranted) {
            startVoiceService(context)
            isEnabled = true
        } else {
            isEnabled = false
            Toast.makeText(
                context,
                "Microphone and Notification permissions are required",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Voice Control ('Hey Panch')",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Play downloaded tracks hands-free using 'Hey Panch play [song]'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { targetState ->
                if (targetState) {
                    val permissionsNeeded = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val allGranted = permissionsNeeded.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        startVoiceService(context)
                        isEnabled = true
                    } else {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                } else {
                    stopVoiceService(context)
                    isEnabled = false
                }
            }
        )
    }
}

private fun startVoiceService(context: Context) {
    try {
        val intent = Intent(context, NativeVoiceService::class.java)
        ContextCompat.startForegroundService(context, intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to start voice service: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun stopVoiceService(context: Context) {
    try {
        val intent = Intent(context, NativeVoiceService::class.java)
        context.stopService(intent)
    } catch (e: Exception) {
        // Ignored
    }
}
