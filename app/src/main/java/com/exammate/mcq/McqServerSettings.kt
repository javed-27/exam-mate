package com.exammate.mcq

import android.content.Context
import android.content.SharedPreferences

interface McqServerSettings {
    var baseUrl: String
    var model: String
}

class SharedPrefsMcqServerSettings(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    ),
) : McqServerSettings {

    override var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    override var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    private companion object {
        const val PREFS_NAME = "mcq_server_settings"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_MODEL = "gemma4:12b-mlx"
    }
}