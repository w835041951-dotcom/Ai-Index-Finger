package com.aiindexfinger.data

import android.content.Context

enum class AppearanceMode(val persistedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromPersistedValue(value: String?): AppearanceMode = entries
            .firstOrNull { it.persistedValue == value }
            ?: System
    }
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun appearanceMode(): AppearanceMode = AppearanceMode.fromPersistedValue(
        preferences.getString(APPEARANCE_MODE_KEY, null),
    )

    fun setAppearanceMode(mode: AppearanceMode) {
        preferences.edit().putString(APPEARANCE_MODE_KEY, mode.persistedValue).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_preferences"
        const val APPEARANCE_MODE_KEY = "appearance_mode"
    }
}
