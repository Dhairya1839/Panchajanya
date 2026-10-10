package com.dn0ne.player

import android.content.Context
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpatialAudioController(private val context: Context) {

    private var virtualizer: Virtualizer? = null
    private var currentAudioSessionId: Int = 0

    private val _isSpatialAudioEnabled = MutableStateFlow(false)
    val isSpatialAudioEnabled: StateFlow<Boolean> = _isSpatialAudioEnabled.asStateFlow()

    private val _spatialStrength = MutableStateFlow<Short>(1000) // Max strength 1000 (100%)
    val spatialStrength: StateFlow<Short> = _spatialStrength.asStateFlow()

    /**
     * Binds the virtualizer to ExoPlayer's active audio session.
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId

        try {
            virtualizer?.release()
            virtualizer = Virtualizer(0, audioSessionId).apply {
                // Force binaural virtualization mode (headphone 3D acoustics)
                forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                setStrength(_spatialStrength.value)
                enabled = _isSpatialAudioEnabled.value
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSpatialAudioEnabled(enabled: Boolean) {
        _isSpatialAudioEnabled.value = enabled
        try {
            virtualizer?.enabled = enabled
            if (enabled) {
                virtualizer?.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
                virtualizer?.setStrength(_spatialStrength.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _spatialStrength.value = clamped
        try {
            virtualizer?.setStrength(clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            virtualizer?.release()
            virtualizer = null
        } catch (_: Exception) {}
    }
}
