package com.dn0ne.player.app.presentation.components.settings

import android.Manifest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dn0ne.player.AutoVolumePreferences

@Composable
fun AutoVolumeSettingsTile(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(AutoVolumePreferences.isEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            isChecked = true
            AutoVolumePreferences.setEnabled(context, true)
        } else {
            isChecked = false
            AutoVolumePreferences.setEnabled(context, false)
            Toast.makeText(context, "Microphone permission is required to detect ambient noise", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column {
                Text(
                    text = "Adaptive Volume",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Automatically adjusts volume to surrounding noise when Bluetooth is connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = { targetState ->
                if (targetState) {
                    val permissionsNeeded = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }

                    val allGranted = permissionsNeeded.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        isChecked = true
                        AutoVolumePreferences.setEnabled(context, true)
                    } else {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                } else {
                    isChecked = false
                    AutoVolumePreferences.setEnabled(context, false)
                }
            }
        )
    }
}
