package com.lazylantern.midgar.models

data class ContextInfo(val appName: String,
                 val versionName: String,
                 val versionCode: Int,
                 val deviceCountry: String,
                 val osVersion: String,
                 val model: String,
                 val manufacturer: String,
                 val isEmulator: Boolean) {

    fun toMap(): HashMap<String, Any> {
        return HashMap<String, Any>().apply {
            put("app_name", appName)
            put("version_name", versionName)
            put("version_code", versionCode)
            put("os_version", osVersion)
            put("country", deviceCountry)
            put("model", model)
            put("manufacturer", manufacturer)
            put("is_emulator", isEmulator)
        }
    }
}