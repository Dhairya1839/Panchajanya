package com.dn0ne.player

import android.content.Context

object TorchSyncPreferences {
    private const val PREFS_NAME = "panchajanya_torch_settings"
    private const val KEY_MODE = "torch_sync_mode"

    fun getMode(context: Context): TorchSyncMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_MODE, TorchSyncMode.OFF.name) ?: TorchSyncMode.OFF.name
        return try {
            TorchSyncMode.valueOf(name)
        } catch (_: Exception) {
            TorchSyncMode.OFF
        }
    }

    fun setMode(context: Context, mode: TorchSyncMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}
