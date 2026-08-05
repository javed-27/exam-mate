package com.exammate.mcq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionStoreTest {

    private class FakeCameraPermissionStore : CameraPermissionStore {
        override var cameraDenied: Boolean = false
    }

    @Test
    fun defaultsToFalse_whenNeverDenied() {
        val store = FakeCameraPermissionStore()

        assertFalse(store.cameraDenied)
    }

    @Test
    fun persistsDeniedFlag_whenSet() {
        val store = FakeCameraPermissionStore()

        store.cameraDenied = true

        assertTrue(store.cameraDenied)
    }

    @Test
    fun resetsDeniedFlag_whenSetBackToFalse() {
        val store = FakeCameraPermissionStore()
        store.cameraDenied = true

        store.cameraDenied = false

        assertFalse(store.cameraDenied)
    }
}
