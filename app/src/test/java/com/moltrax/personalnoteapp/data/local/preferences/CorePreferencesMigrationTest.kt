package com.moltrax.personalnoteapp.data.local.preferences

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CorePreferencesMigrationTest {
    @Test fun removesRetiredSettingsWithoutChangingLocalPreferences() = runBlocking {
        val theme = stringPreferencesKey("theme_mode")
        val updates = booleanPreferencesKey("auto_update")
        val retired = stringPreferencesKey("retired_api_key")
        val before = mutablePreferencesOf(theme to "light", updates to false, retired to "unused")
        assertTrue(CorePreferencesMigration.shouldMigrate(before))
        val after = CorePreferencesMigration.migrate(before)
        assertEquals("light", after[theme])
        assertEquals(false, after[updates])
        assertNull(after[retired])
        assertFalse(CorePreferencesMigration.shouldMigrate(after))
        assertEquals("unused", before[retired])
    }
}
