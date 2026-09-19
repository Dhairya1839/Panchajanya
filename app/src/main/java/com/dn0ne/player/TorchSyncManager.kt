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
    DISCO,   // Softened beat pulse with natural decay
    FADE     // Continuous ambient wave glow
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
    private var energyLevel = 0f

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
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let { computeWaveformEnergy(it) }
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
            val pcm = (sample.toInt() and 0xFF) - 128
            sum += abs(pcm)
        }
        val avg = sum / waveform.size
        energyLevel = (avg / 55.0).toFloat().coerceIn(0f, 1f)
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
            var envelope = 0f
            var smoothedFade = 0f
            var lastTorchState = false

            while (isActive && currentMode != TorchSyncMode.OFF) {
                val camId = cameraId ?: break

                when (currentMode) {
                    TorchSyncMode.DISCO -> {
                        // Attack: Hit fast if beat energy is higher than current level
                        if (energyLevel > envelope) {
                            envelope = energyLevel
                        } else {
                            // Release: Decay gradually instead of snapping shut immediately
                            envelope *= 0.72f
                        }

                        if (supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (envelope > 0.18f) {
                                val strength = (envelope * maxStrengthLevel).toInt().coerceIn(1, maxStrengthLevel)
                                try {
                                    cameraManager.turnOnTorchWithStrengthLevel(camId, strength)
                                    lastTorchState = true
                                } catch (_: Exception) {}
                            } else if (lastTorchState) {
                                try {
                                    cameraManager.setTorchMode(camId, false)
                                    lastTorchState = false
                                } catch (_: Exception) {}
                            }
                            delay(30) // Fast 33 FPS update for smooth decay
                        } else {
                            // Binary fallback: hold the light open through the decay tail
                            val shouldTurnOn = envelope > 0.38f
                            if (shouldTurnOn != lastTorchState) {
                                try {
                                    cameraManager.setTorchMode(camId, shouldTurnOn)
                                    lastTorchState = shouldTurnOn
                                } catch (_: Exception) {}
                            }
                            delay(45)
                        }
                    }

                    TorchSyncMode.FADE -> {
                        smoothedFade += (energyLevel - smoothedFade) * 0.25f

                        if (supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                if (smoothedFade > 0.08f) {
                                    val targetStrength = (smoothedFade * maxStrengthLevel).toInt().coerceIn(1, maxStrengthLevel)
                                    cameraManager.turnOnTorchWithStrengthLevel(camId, targetStrength)
                                    lastTorchState = true
                                } else if (lastTorchState) {
                                    cameraManager.setTorchMode(camId, false)
                                    lastTorchState = false
                                }
                            } catch (_: Exception) {}
                            delay(35)
                        } else {
                            val isPulseOn = smoothedFade > 0.30f
                            if (isPulseOn != lastTorchState) {
                                try {
                                    cameraManager.setTorchMode(camId, isPulseOn)
                                    lastTorchState = isPulseOn
                                } catch (_: Exception) {}
                            }
                            delay(90)
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
