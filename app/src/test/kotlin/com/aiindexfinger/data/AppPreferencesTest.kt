package com.aiindexfinger.data

import com.aiindexfinger.usesDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `persisted appearance values round trip`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromPersistedValue(mode.persistedValue))
        }
    }

    @Test
    fun `missing and unknown appearance values use system default`() {
        assertEquals(AppearanceMode.System, AppearanceMode.fromPersistedValue(null))
        assertEquals(AppearanceMode.System, AppearanceMode.fromPersistedValue("future-mode"))
    }

    @Test
    fun `appearance modes resolve dark theme deterministically`() {
        assertEquals(false, AppearanceMode.System.usesDarkTheme(false))
        assertEquals(true, AppearanceMode.System.usesDarkTheme(true))
        assertEquals(false, AppearanceMode.Light.usesDarkTheme(true))
        assertEquals(true, AppearanceMode.Dark.usesDarkTheme(false))
    }
}
