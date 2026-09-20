package com.dn0ne.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AutoVolumeManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var recordingJob: Job? = null
    private var isRunning = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private val prefs = context.getSharedPreferences("panchajanya_audio_settings", Context.MODE_PRIVATE)

    // Baseline volume set by the user manually
    private var userBaseVolumeStep = -1
    private var lastAppliedVolumeStep = -1

    // Smoothed ambient noise tracker
    private var smoothedDb = 45.0

    // Debounce / Reaction Delay variables
    private var pendingTargetStep = -1
    private var pendingStepStartTime = 0L
    private val reactionDelayMs = 600L // Noise must persist for 600ms before changing volume

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "auto_volume_enabled") {
            val enabled = AutoVolumePreferences.isEnabled(context)
            if (!enabled) {
                stop()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @SuppressLint("WakelockTimeout")
    fun start() {
        if (isRunning) return
        if (!AutoVolumePreferences.isEnabled(context)) return
        if (!isBluetoothOutputConnected()) return

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        userBaseVolumeStep = currentVol
        lastAppliedVolumeStep = currentVol
        pendingTargetStep = currentVol

        // Acquire a partial wake lock so the CPU keeps processing mic input while locked
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Panchajanya:AutoVolumeWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire()

        isRunning = true
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            monitorAndAdjustLoop()
        }
    }

    fun stop() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        userBaseVolumeStep = -1
        lastAppliedVolumeStep = -1
        pendingTargetStep = -1

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private suspend fun monitorAndAdjustLoop() {
        var audioRecord: AudioRecord? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            val buffer = ShortArray(bufferSize)
            audioRecord.startRecording()

            while (isRunning && kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (!AutoVolumePreferences.isEnabled(context) || !isBluetoothOutputConnected()) {
                    break
                }

                // Detect manual user adjustments (physical volume keys)
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol != lastAppliedVolumeStep) {
                    userBaseVolumeStep = currentVol
                    lastAppliedVolumeStep = currentVol
                    pendingTargetStep = currentVol
                }

                val readCount = audioRecord.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    val instantDb = calculateDecibels(buffer, readCount)
                    
                    smoothedDb = (smoothedDb * 0.70) + (instantDb * 0.30)
                    processVolumeWithDelay(smoothedDb)
                }

                delay(200)
            }
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            isRunning = false
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun calculateDecibels(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / readSize)
        return if (rms > 0) 20 * log10(rms) else 0.0
    }

    private fun processVolumeWithDelay(currentDb: Double) {
        if (userBaseVolumeStep < 0) return

        val maxSystemSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val baselineAcousticDb = 38.0 + ((userBaseVolumeStep.toDouble() / maxSystemSteps) * 32.0)

        val noiseDeadzoneDb = 5.0
        val rawExcess = currentDb - baselineAcousticDb
        val excessNoiseDb = if (rawExcess > noiseDeadzoneDb) rawExcess - noiseDeadzoneDb else 0.0

        val stepBoost = (excessNoiseDb / 3.0).roundToInt()
        val calculatedTargetStep = (userBaseVolumeStep + stepBoost).coerceIn(userBaseVolumeStep, maxSystemSteps)

        val currentTime = System.currentTimeMillis()

        if (calculatedTargetStep != currentVol) {
            if (calculatedTargetStep != pendingTargetStep) {
                pendingTargetStep = calculatedTargetStep
                pendingStepStartTime = currentTime
            } else if (currentTime - pendingStepStartTime >= reactionDelayMs) {
                lastAppliedVolumeStep = calculatedTargetStep
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    calculatedTargetStep,
                    0
                )
            }
        } else {
            pendingTargetStep = currentVol
        }
    }

    fun isBluetoothOutputConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }
}
