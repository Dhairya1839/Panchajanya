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
    DISCO,   // Softened, dynamic beat pulse
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
        // Calibrated sensitivity
        energyLevel = (avg / 45.0).toFloat().coerceIn(0f, 1f)
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
            var lastStrengthSent = -1
            var isTorchOn = false

            while (isActive && currentMode != TorchSyncMode.OFF) {
                val camId = cameraId ?: break

                when (currentMode) {
                    TorchSyncMode.DISCO -> {
                        // Attack & Decay smoothing
                        if (energyLevel > envelope) {
                            envelope = energyLevel
                        } else {
                            envelope *= 0.70f // Soft natural decay
                        }

                        if (supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (envelope > 0.20f) {
                                val targetStrength = (envelope * maxStrengthLevel).toInt().coerceIn(1, maxStrengthLevel)
                                if (targetStrength != lastStrengthSent) {
                                    try {
                                        cameraManager.turnOnTorchWithStrengthLevel(camId, targetStrength)
                                        lastStrengthSent = targetStrength
                                        isTorchOn = true
                                    } catch (_: Exception) {}
                                }
                            } else if (isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, false)
                                    lastStrengthSent = 0
                                    isTorchOn = false
                                } catch (_: Exception) {}
                            }
                            delay(50) // 20 updates/sec to prevent camera driver locking
                        } else {
                            // Standard device path: comfortable beat threshold
                            val shouldTurnOn = envelope > 0.40f
                            if (shouldTurnOn != isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, shouldTurnOn)
                                    isTorchOn = shouldTurnOn
                                } catch (_: Exception) {}
                            }
                            delay(60)
                        }
                    }

                    TorchSyncMode.FADE -> {
                        envelope += (energyLevel - envelope) * 0.30f

                        if (supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (envelope > 0.10f) {
                                val targetStrength = (envelope * maxStrengthLevel).toInt().coerceIn(1, maxStrengthLevel)
                                if (targetStrength != lastStrengthSent) {
                                    try {
                                        cameraManager.turnOnTorchWithStrengthLevel(camId, targetStrength)
                                        lastStrengthSent = targetStrength
                                        isTorchOn = true
                                    } catch (_: Exception) {}
                                }
                            } else if (isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, false)
                                    lastStrengthSent = 0
                                    isTorchOn = false
                                } catch (_: Exception) {}
                            }
                            delay(50)
                        } else {
                            val isPulseOn = envelope > 0.32f
                            if (isPulseOn != isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, isPulseOn)
                                    isTorchOn = isPulseOn
                                } catch (_: Exception) {}
                            }
                            delay(100)
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
