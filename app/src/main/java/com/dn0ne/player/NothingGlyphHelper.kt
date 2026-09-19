package com.dn0ne.player

import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager

class NothingGlyphHelper(private val context: Context) {

    private var glyphManager: GlyphManager? = null
    private var isBound = false

    val isNothingPhone: Boolean = Build.MANUFACTURER.equals("Nothing", ignoreCase = true)

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            isBound = true
            try {
                glyphManager?.register(Common.DEVICE_20111) // Compatible with Phone (1) & (2)
                glyphManager?.openSession()
            } catch (_: GlyphException) {}
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isBound = false
        }
    }

    fun init() {
        if (!isNothingPhone) return
        try {
            glyphManager = GlyphManager.getInstance(context.applicationContext)
            glyphManager?.init(callback)
        } catch (_: Exception) {}
    }

    /**
     * Strobe mode: flashes all glyph zones on beat peaks.
     */
    fun setStrobe(turnOn: Boolean) {
        if (!isBound || glyphManager == null) return
        try {
            if (turnOn) {
                // Max brightness (100) across all zones
                val frame: GlyphFrame = glyphManager!!.glyphFrameBuilder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .buildChannelD()
                    .buildChannelE()
                    .build()
                glyphManager?.animate(frame)
            } else {
                glyphManager?.turnOff()
            }
        } catch (_: Exception) {}
    }

    /**
     * Fade mode: passes normalized energy (0.0 to 1.0) directly to variable Glyph brightness.
     */
    fun setBrightness(normalized: Float) {
        if (!isBound || glyphManager == null) return
        try {
            val brightness = (normalized * 100).toInt().coerceIn(0, 100)
            if (brightness > 5) {
                val frame: GlyphFrame = glyphManager!!.glyphFrameBuilder
                    .buildChannelA(brightness)
                    .buildChannelB(brightness)
                    .buildChannelC(brightness)
                    .buildChannelD(brightness)
                    .buildChannelE(brightness)
                    .build()
                glyphManager?.animate(frame)
            } else {
                glyphManager?.turnOff()
            }
        } catch (_: Exception) {}
    }

    fun turnOff() {
        if (!isBound) return
        try {
            glyphManager?.turnOff()
        } catch (_: Exception) {}
    }

    fun release() {
        if (!isBound) return
        try {
            glyphManager?.turnOff()
            glyphManager?.closeSession()
            glyphManager?.unInit()
        } catch (_: Exception) {}
        isBound = false
    }
}
