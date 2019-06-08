package com.lazylantern.midgar.models

data class Event(val type: String,
                 val name: String,
                 val timestampMs: Long,
                 val platform: String,
                 val sdk: String,
                 val deviceId: String,
                 val sessionId: String){
    companion object {
        const val TYPE_BACKGROUND = "background"
        const val TYPE_FOREGROUND = "foreground"
        const val TYPE_IMPRESSION = "impression"
        const val PLATFORM_ANDROID = "android"
        const val SDK_KOTLIN = "kotlin"
    }

    fun toMap(): HashMap<String, Any>{
         return HashMap<String, Any>().apply {
            put("type", type)
            put("screen",  name)
            put("timestamp", timestampMs)
            put("platform", platform)
            put("sdk", sdk)
            put("device_id", deviceId)
            put("session_id", sessionId)
        }
    }
}