package com.dn0ne.player.app.presentation.components.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.content.ContextCompat
import com.dn0ne.player.AutoVolumePreferences
import com.dn0ne.player.EqualizerController
import com.dn0ne.player.R
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import com.dn0ne.player.app.presentation.components.topbar.ColumnWithCollapsibleTopBar
import com.dn0ne.player.core.data.Settings
import kotlinx.coroutines.launch

@Composable
fun PlaybackSettings(
    settings: Settings,
    equalizerController: EqualizerController,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var collapseFraction by remember {
        mutableFloatStateOf(0f)
    }

    var isAutoVolumeEnabled by remember {
        mutableStateOf(AutoVolumePreferences.isEnabled(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }

        if (recordAudioGranted && bluetoothGranted) {
            isAutoVolumeEnabled = true
            AutoVolumePreferences.setEnabled(context, true)
        } else {
            isAutoVolumeEnabled = false
            AutoVolumePreferences.setEnabled(context, false)
            Toast.makeText(
                context,
                "Microphone and Bluetooth permissions are required for Adaptive Volume",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ColumnWithCollapsibleTopBar(
        topBarContent = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = context.resources.getString(R.string.back)
                )
            }

            Text(
                text = context.resources.getString(R.string.playback),
                fontSize = lerp(
                    MaterialTheme.typography.titleLarge.fontSize,
                    MaterialTheme.typography.displaySmall.fontSize,
                    collapseFraction
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
            )
        },
        collapseFraction = {
            collapseFraction = it
        },
        contentPadding = PaddingValues(horizontal = 28.dp),
        contentHorizontalAlignment = Alignment.CenterHorizontally,
        contentVerticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        val coroutineScope = rememberCoroutineScope()
        var handleAudioFocus by remember {
            mutableStateOf(settings.handleAudioFocus)
        }
        SettingSwitch(
            title = context.resources.getString(R.string.audio_focus),
            supportingText = context.resources.getString(R.string.audio_focus_explain),
            icon = Icons.Rounded.FilterCenterFocus,
            isChecked = handleAudioFocus,
            onCheckedChange = {
                settings.handleAudioFocus = it
                handleAudioFocus = it
                coroutineScope.launch {
                    SnackbarController.sendEvent(
                        SnackbarEvent(
                            message = R.string.changes_will_take_effect_on_next_launch
                        )
                    )
                }
            }
        )

        var jumpToBeginning by remember {
            mutableStateOf(settings.jumpToBeginning)
        }
        SettingSwitch(
            title = context.resources.getString(R.string.jump_to_beginning),
            supportingText = context.resources.getString(R.string.jump_to_beginning_explain),
            icon = Icons.Rounded.SkipPrevious,
            isChecked = jumpToBeginning,
            onCheckedChange = {
                settings.jumpToBeginning = it
                jumpToBeginning = it
            }
        )

        val isEqEnabled by equalizerController.isEqEnabled.collectAsState()
        SettingSwitch(
            title = context.resources.getString(R.string.equalizer),
            supportingText = context.resources.getString(R.string.equalizer_explain),
            icon = Icons.Rounded.Equalizer,
            isChecked = isEqEnabled,
            onCheckedChange = equalizerController::updateIsEqEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = isEqEnabled
        ) {
            EqualizerSettings(
                equalizerController = equalizerController,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        SettingSwitch(
            title = "Adaptive volume",
            supportingText = "Automatically adjusts volume to surrounding noise when Bluetooth is connected (25% - 50%)",
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            isChecked = isAutoVolumeEnabled,
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
                        isAutoVolumeEnabled = true
                        AutoVolumePreferences.setEnabled(context, true)
                    } else {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                } else {
                    isAutoVolumeEnabled = false
                    AutoVolumePreferences.setEnabled(context, false)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EqualizerSettings(
    equalizerController: EqualizerController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val lowerLevelLimit by equalizerController.lowerLevelLimit.collectAsState()
        val upperLevelLimit by equalizerController.upperLevelLimit.collectAsState()
        val bandFrequencies by equalizerController.bandFrequencies.collectAsState()
        val bandLevels by equalizerController.bandLevels.collectAsState()
        bandLevels?.fastForEachIndexed { index, level ->
            SettingSlider(
                title = bandFrequencies?.get(index) ?: "",
                value = level.toFloat(),
                valueToShow = "${level / 100f}dB",
                onValueChange = {
                    bandLevels?.let { bandLevels ->
                        equalizerController.updateBandLevels(
                            bandLevels.toMutableList().apply {
                                set(index, it.toInt().toShort())
                            }
                        )
                    }
                },
                onValueChangeFinished = {},
                valueRange = lowerLevelLimit.toFloat()..upperLevelLimit.toFloat(),
            )
        }

        val context = LocalContext.current
        FilledTonalButton(
            onClick = equalizerController::resetBandLevels
        ) {
            Text(text = context.resources.getString(R.string.reset))
        }
    }
}
