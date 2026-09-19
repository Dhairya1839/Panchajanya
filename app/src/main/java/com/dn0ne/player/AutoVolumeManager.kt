package com.dn0ne.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AutoVolumeManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recordingJob: Job? = null
    private var isRunning = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun start() {
        if (isRunning) return
        if (!isBluetoothOutputConnected()) return

        isRunning = true
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            monitorAndAdjustLoop()
        }
    }

    fun stop() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
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
                if (!isBluetoothOutputConnected()) {
                    delay(2000)
                    continue
                }

                val readCount = audioRecord.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    val db = calculateDecibels(buffer, readCount)
                    adjustVolumeForDecibels(db)
                }

                delay(2000)
            }
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
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

    private fun adjustVolumeForDecibels(db: Double) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val targetVolumeLevel = when {
            db < 52 -> (maxVolume * 0.40).toInt()
            db < 66 -> (maxVolume * 0.60).toInt()
            db < 78 -> (maxVolume * 0.75).toInt()
            else    -> (maxVolume * 0.85).toInt()
        }

        if (abs(targetVolumeLevel - currentVolume) >= 2) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolumeLevel,
                0
            )
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
