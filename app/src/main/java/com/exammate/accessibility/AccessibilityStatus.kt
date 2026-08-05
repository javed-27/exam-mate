package com.exammate.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

fun accessibilityServiceId(context: Context, serviceClass: Class<*>): String =
    ComponentName(context.packageName, serviceClass.name).flattenToShortString()

fun isAccessibilityServiceEnabled(
    serviceId: String,
    enabledServiceIds: Collection<String>,
): Boolean = enabledServiceIds.contains(serviceId)

fun isAccessibilityServiceEnabled(
    context: Context,
    serviceClass: Class<*>,
): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val enabledServiceIds = manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .map { it.id }
    return isAccessibilityServiceEnabled(
        serviceId = accessibilityServiceId(context, serviceClass),
        enabledServiceIds = enabledServiceIds,
    )
}
