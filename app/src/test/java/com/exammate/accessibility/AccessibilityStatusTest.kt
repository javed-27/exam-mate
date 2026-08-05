package com.exammate.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {

    private val serviceId = "com.exammate/.accessibility.McqAccessibilityService"

    @Test
    fun returnsTrue_whenServiceIdIsEnabled() {
        val enabledServiceIds = listOf(
            serviceId,
            "com.some.other/app",
        )

        assertTrue(isAccessibilityServiceEnabled(serviceId, enabledServiceIds))
    }

    @Test
    fun returnsFalse_whenServiceIdIsNotEnabled() {
        val enabledServiceIds = listOf("com.some.other/app")

        assertFalse(isAccessibilityServiceEnabled(serviceId, enabledServiceIds))
    }

    @Test
    fun returnsFalse_whenNoServicesAreEnabled() {
        assertFalse(isAccessibilityServiceEnabled(serviceId, emptyList()))
    }

    @Test
    fun returnsFalse_whenServiceIdIsBlank() {
        val enabledServiceIds = listOf(serviceId)

        assertFalse(isAccessibilityServiceEnabled("", enabledServiceIds))
    }
}
