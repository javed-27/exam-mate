package com.exammate.ui.mcq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.exammate.accessibility.McqAccessibilityService
import com.exammate.accessibility.isAccessibilityServiceEnabled

interface McqPermissionChecker {
    fun isCameraGranted(): Boolean
    fun isAccessibilityEnabled(): Boolean
}

class AndroidPermissionChecker(private val context: Context) : McqPermissionChecker {

    override fun isCameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun isAccessibilityEnabled(): Boolean =
        isAccessibilityServiceEnabled(context, McqAccessibilityService::class.java)
}
