package com.dn0ne.player.jam

import org.json.JSONObject

enum class JamRole {
    NONE,
    HOST,
    GUEST
}

enum class JamAction {
    PLAY,
    PAUSE,
    SEEK,
    FILE_INCOMING,
    READY
}

data class JamCommand(
    val action: JamAction,
    val positionMs: Long = 0L,
    val targetTimestamp: Long = 0L,
    val fileName: String = ""
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("action", action.name)
            put("positionMs", positionMs)
            put("targetTimestamp", targetTimestamp)
            put("fileName", fileName)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): JamCommand {
            val json = JSONObject(jsonStr)
            return JamCommand(
                action = JamAction.valueOf(json.getString("action")),
                positionMs = json.optLong("positionMs", 0L),
                targetTimestamp = json.optLong("targetTimestamp", 0L),
                fileName = json.optString("fileName", "")
            )
        }
    }
}

data class DiscoveredHost(
    val endpointId: String,
    val endpointName: String
)
