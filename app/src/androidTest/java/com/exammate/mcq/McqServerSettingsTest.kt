package com.exammate.mcq

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class McqServerSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun defaults_matchExpectedServerAndModel() {
        context.getSharedPreferences("mcq_server_settings", 0).edit().clear().commit()
        val settings = SharedPrefsMcqServerSettings(context)

        assertEquals("http://localhost:11434", settings.baseUrl)
        assertEquals("gemma4:12b-mlx", settings.model)
    }

    @Test
    fun changedValues_persistAcrossInstances() {
        context.getSharedPreferences("mcq_server_settings", 0).edit().clear().commit()
        val initial = SharedPrefsMcqServerSettings(context)

        initial.baseUrl = "http://192.168.1.10:11434"
        initial.model = "llama3"

        val reloaded = SharedPrefsMcqServerSettings(context)
        assertEquals("http://192.168.1.10:11434", reloaded.baseUrl)
        assertEquals("llama3", reloaded.model)
    }
}