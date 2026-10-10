package com.dn0ne.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.CountDownTimer
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dn0ne.player.core.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.get
import java.lang.Exception

private class EqualizerSettings(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("equalizer-settings", Context.MODE_PRIVATE)

    private val isEnabledKey = "is-equalizer-enabled"
    private val bandFrequenciesKey = "band-frequencies"
    private val lowerLevelLimitKey = "lower-level-limit"
    private val upperLevelLimitKey = "upper-level-limit"
    private val bandLevelsKey = "band-levels"

    var isEnabled: Boolean
        get() = sharedPreferences.getBoolean(isEnabledKey, false)
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(isEnabledKey, value)
                apply()
            }
        }

    var bandFrequencies: List<String>?
        get() = sharedPreferences.getString(bandFrequenciesKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandFrequenciesKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }

    var lowerLevelLimit: Int
        get() = sharedPreferences.getInt(lowerLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(lowerLevelLimitKey, value)
                apply()
            }
        }

    var upperLevelLimit: Int
        get() = sharedPreferences.getInt(upperLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(upperLevelLimitKey, value)
                apply()
            }
        }

    var bandLevels: List<Short>?
        get() = sharedPreferences.getString(bandLevelsKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandLevelsKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }
}

class EqualizerController(context: Context) {
    private val _settings = EqualizerSettings(context)
    private var _audioSessionId: Int? = null
    var equalizer: Equalizer? = null
        private set

    init {
        firstLaunchInit()
    }

    private val _isEqEnabled = MutableStateFlow(_settings.isEnabled)
    val isEqEnabled = _isEqEnabled.asStateFlow()
    fun updateIsEqEnabled(value: Boolean) {
        _isEqEnabled.update { value }
        _settings.isEnabled = value

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private val _bandFrequencies = MutableStateFlow<List<String>?>(_settings.bandFrequencies)
    val bandFrequencies = _bandFrequencies.asStateFlow()

    private val _lowerLevelLimit = MutableStateFlow<Short>(_settings.lowerLevelLimit.toShort())
    val lowerLevelLimit = _lowerLevelLimit.asStateFlow()

    private val _upperLevelLimit = MutableStateFlow<Short>(_settings.upperLevelLimit.toShort())
    val upperLevelLimit = _upperLevelLimit.asStateFlow()

    private val _bandLevels = MutableStateFlow(_settings.bandLevels)
    val bandLevels = _bandLevels.asStateFlow()
    fun updateBandLevels(levels: List<Short>) {
        _bandLevels.update { levels }
        _settings.bandLevels = levels

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    fun updateEqualizer(audioSessionId: Int) {
        equalizer?.release()

        _audioSessionId = audioSessionId

        if (!_settings.isEnabled) return

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                _settings.bandLevels?.forEachIndexed { band, level ->
                    setBandLevel(band.toShort(), level)
                }
            }
        } catch (e: Exception) {
            Log.e("EqualizerController", "Failed to update equalizer", e)
        }
    }

    fun releaseEqualizer() {
        equalizer?.release()
        equalizer = null
    }

    fun resetBandLevels() {
        _settings.bandLevels?.let { bandLevels ->
            val defaultLevels = List<Short>(bandLevels.size) { 0 }
            _settings.bandLevels = defaultLevels
            _bandLevels.update {
                defaultLevels
            }
        }

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private fun firstLaunchInit() {
        if (_settings.bandFrequencies == null || _settings.bandLevels == null) {
            try {
                val audioTrack = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(44100)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                    .build()

                Equalizer(0, audioTrack.audioSessionId).run {
                    enabled = false

                    val defaultLevels = List<Short>(numberOfBands.toInt()) { 0 }
                    _settings.bandLevels = defaultLevels

                    val bandFreqs = List(numberOfBands.toInt()) {
                        getBandFreqRange(it.toShort()).joinToString("-") {
                            (it / 1000).toString()
                        } + "Hz"
                    }
                    _settings.bandFrequencies = bandFreqs

                    val lowerLevelLimit = bandLevelRange[0]
                    _settings.lowerLevelLimit = lowerLevelLimit.toInt()

                    val upperLevelLimit = bandLevelRange[1]
                    _settings.upperLevelLimit = upperLevelLimit.toInt()

