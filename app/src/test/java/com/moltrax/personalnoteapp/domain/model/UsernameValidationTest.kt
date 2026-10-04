package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [isValidUsername] rules — the public identifier for friends/spaces (Phase 3+).
 */
class UsernameValidationTest {

    @Test
    fun `typical names pass`() {
        assertTrue(isValidUsername("moltrax"))
        assertTrue(isValidUsername("dev_99"))
        assertTrue(isValidUsername("a.b_c"))
    }

    @Test
    fun `too short or too long fails`() {
        assertFalse(isValidUsername("ab"))
        assertFalse(isValidUsername(""))
        assertFalse(isValidUsername("a".repeat(21)))
        assertTrue(isValidUsername("a".repeat(20)))
    }

    @Test
    fun `must start with letter or digit and use safe chars`() {
        assertFalse(isValidUsername("_moltrax"))
        assertFalse(isValidUsername(".moltrax"))
        assertFalse(isValidUsername("mol trax"))
        assertFalse(isValidUsername("mol@trax"))
        assertFalse(isValidUsername("moltrax!"))
    }
}
