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
    FADE     // Smooth brightness modulation
}

class TorchSyncManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val glyphHelper = NothingGlyphHelper(context)
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
        if (glyphHelper.isNothingPhone) {
            glyphHelper.init()
        } else {
            findBackCameraWithFlash()
        }
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
                if (glyphHelper.isNothingPhone) {
                    // --- NOTHING PHONE GLYPH PATH ---
                    when (currentMode) {
                        TorchSyncMode.DISCO -> {
                            val beatOn = energyLevel > 0.45f
                            glyphHelper.setStrobe(beatOn)
                            delay(60)
                        }
                        TorchSyncMode.FADE -> {
                            smoothedFade += (energyLevel - smoothedFade) * 0.35f
                            glyphHelper.setBrightness(smoothedFade)
                            delay(40)
                        }
                        TorchSyncMode.OFF -> break
                    }
                } else {
                    // --- STANDARD CAMERA FLASH PATH ---
                    val camId = cameraId ?: break

                    when (currentMode) {
                        TorchSyncMode.DISCO -> {
                            val shouldTurnOn = energyLevel > 0.45f
                            try {
                                cameraManager.setTorchMode(camId, shouldTurnOn)
                            } catch (_: Exception) {}
                            delay(60)
                        }

                        TorchSyncMode.FADE -> {
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
                                delay(40)
                            } else {
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
            }

            turnOffAll()
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        turnOffAll()
    }

    private fun turnOffAll() {
        if (glyphHelper.isNothingPhone) {
            glyphHelper.turnOff()
        } else {
            cameraId?.let { id ->
                try {
                    cameraManager.setTorchMode(id, false)
                } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        stop()
        releaseVisualizer()
        glyphHelper.release()
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (_: Exception) {}
    }
}
