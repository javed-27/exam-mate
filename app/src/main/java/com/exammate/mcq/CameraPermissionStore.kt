package com.exammate.mcq

import android.content.Context
import android.content.SharedPreferences

interface CameraPermissionStore {
    var cameraDenied: Boolean
}

class SharedPrefsCameraPermissionStore(context: Context) : CameraPermissionStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var cameraDenied: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_DENIED, false)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_DENIED, value).apply()

    private companion object {
        const val PREFS_NAME = "mcq_permissions"
        const val KEY_CAMERA_DENIED = "camera_permission_denied"
    }
}
