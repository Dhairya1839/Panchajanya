package com.dn0ne.player

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class TorchSyncMode {
    OFF,
    DISCO,   // Punchy, musical pulse with smooth beat-hold
    FADE     // Slower, breathing ambient pulse
}

class TorchSyncManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    private var visualizer: Visualizer? = null
    private var syncJob: Job? = null

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
        // 0.0 to 1.0 energy normalization
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
            var isTorchOn = false

            while (isActive && currentMode != TorchSyncMode.OFF) {
                val camId = cameraId ?: break

                when (currentMode) {
                    TorchSyncMode.DISCO -> {
                        // Beat trigger with minimum hold-open time for a smooth light pulse
                        if (energyLevel > 0.42f) {
                            if (!isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, true)
                                    isTorchOn = true
                                } catch (_: Exception) {}
                            }
                            // Dwell time: keeps the light lit smoothly through the beat peak
                            delay(85)
                        } else {
                            if (isTorchOn) {
                                try {
                                    cameraManager.setTorchMode(camId, false)
                                    isTorchOn = false
                                } catch (_: Exception) {}
                            }
                            delay(50)
                        }
                    }

                    TorchSyncMode.FADE -> {
                        // Leaky integrator to create a soft, breathing swell
                        smoothedFade += (energyLevel - smoothedFade) * 0.35f
                        val shouldTurnOn = smoothedFade > 0.36f

                        if (shouldTurnOn != isTorchOn) {
                            try {
                                cameraManager.setTorchMode(camId, shouldTurnOn)
                                isTorchOn = shouldTurnOn
                            } catch (_: Exception) {}
                        }
                        delay(110)
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
