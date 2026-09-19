package com.dn0ne.player

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.audiofx.Visualizer
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class TorchSyncMode {
    OFF,
    DISCO,   // Sharp On / Off beat pulse
    FADE     // Smooth brightness modulation (API 33+) or smoothed strobe fallback
}

class TorchSyncManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    private var visualizer: Visualizer? = null
    private var syncJob: Job? = null

    private var maxStrengthLevel = 1
    private var supportsStrengthControl = false

    @Volatile
    private var currentMode = TorchSyncMode.OFF

    @Volatile
    private var energyLevel = 0f // 0.0 to 1.0 based on music energy

    init {
        findBackCameraWithFlash()
    }

    private fun findBackCameraWithFlash() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        if (maxLevel > 1) {
                            maxStrengthLevel = maxLevel
                            supportsStrengthControl = true
                        }
                    }
                    break
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        releaseVisualizer()

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0] // Minimal size for fast low-latency response
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let {
                            computeWaveformEnergy(it)
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (_: Exception) {}
    }

    private fun computeWaveformEnergy(waveform: ByteArray) {
        var sum = 0.0
        for (sample in waveform) {
            // Unsigned 8-bit to signed PCM (-128 to 127)
            val pcm = (sample.toInt() and 0xFF) - 128
            sum += abs(pcm)
        }
        val avg = sum / waveform.size
        // Normalize 0.0 to 1.0 (typical peak speech/music hits 35-70 avg)
        energyLevel = (avg / 60.0).toFloat().coerceIn(0f, 1f)
    }

    fun setMode(mode: TorchSyncMode) {
        currentMode = mode
        if (mode == TorchSyncMode.OFF) {
            stop()
        } else {
            startLoop()
        }
    }

    fun getMode(): TorchSyncMode = currentMode

    private fun startLoop() {
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.Default).launch {
            var smoothedFade = 0f

            while (isActive && currentMode != TorchSyncMode.OFF) {
                val camId = cameraId ?: break

                when (currentMode) {
                    TorchSyncMode.DISCO -> {
                        // Strobe beat detection
                        val shouldTurnOn = energyLevel > 0.45f
                        try {
                            cameraManager.setTorchMode(camId, shouldTurnOn)
                        } catch (_: Exception) {}
                        delay(60) // Strobe tick interval
                    }

                    TorchSyncMode.FADE -> {
                        // Smoothly interpolate energy for a fade in / fade out effect
                        smoothedFade += (energyLevel - smoothedFade) * 0.35f

                        if (supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                if (smoothedFade > 0.08f) {
                                    val targetStrength = (smoothedFade * maxStrengthLevel).toInt().coerceIn(1, maxStrengthLevel)
                                    cameraManager.turnOnTorchWithStrengthLevel(camId, targetStrength)
                                } else {
                                    cameraManager.setTorchMode(camId, false)
                                }
                            } catch (_: Exception) {}
                            delay(40) // Fast 25fps refresh for smooth fading
                        } else {
                            // Fallback for hardware without multi-level brightness: rhythmic pulsing
                            val isPulseOn = smoothedFade > 0.35f
                            try {
                                cameraManager.setTorchMode(camId, isPulseOn)
                            } catch (_: Exception) {}
                            delay(120)
                        }
                    }

                    TorchSyncMode.OFF -> break
                }
            }

            turnOffTorch()
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        turnOffTorch()
    }

    private fun turnOffTorch() {
        cameraId?.let { id ->
            try {
                cameraManager.setTorchMode(id, false)
            } catch (_: Exception) {}
        }
    }

    fun release() {
        stop()
        releaseVisualizer()
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (_: Exception) {}
    }
}
