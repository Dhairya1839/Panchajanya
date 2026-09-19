package com.dn0ne.player

import android.content.Context
import android.content.SharedPreferences

object AutoVolumePreferences {
    private const val PREFS_NAME = "panchajanya_audio_settings"
    private const val KEY_AUTO_VOLUME = "auto_volume_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_VOLUME, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_VOLUME, enabled).apply()
    }
}
