package com.moltrax.personalnoteapp.domain.util

import com.moltrax.personalnoteapp.domain.model.Task
import org.junit.Assert.*
import org.junit.Test

class TaskTagMatchingTest {
    private val task = Task(title = "Tagged", categoryNames = setOf("Work", "Home"),
        categoryOrders = mapOf("Work" to 9L, "Home" to -2L))

    @Test fun anyAndAllUseCaseInsensitiveMembership() {
        assertTrue(task.matchesTags(setOf("work", "HOME"), matchAll = true))
        assertTrue(task.matchesTags(setOf("missing", "WORK"), matchAll = false))
        assertFalse(task.matchesTags(setOf("missing", "WORK"), matchAll = true))
        assertFalse(task.matchesTags(setOf("missing"), matchAll = false))
    }

    @Test fun emptySelectionRetainsTheRepositoryAnyAllDistinction() {
        for (tags in listOf(emptySet(), setOf("Work"))) {
            val value = task.copy(categoryNames = tags)
            assertFalse(value.matchesTags(emptySet(), matchAll = false))
            assertTrue(value.matchesTags(emptySet(), matchAll = true))
        }
    }

    @Test fun matchingDoesNotTrimNormalizeOrConsultLegacyCategory() {
        assertFalse(task.matchesTags(setOf(" Work "), matchAll = false))
        val legacyOnly = task.copy(categoryNames = emptySet(), category = "Work")
        assertFalse(legacyOnly.matchesTags(setOf("Work"), matchAll = false))
        val unicode = task.copy(categoryNames = setOf("İ", "Straße"))
        assertTrue(unicode.matchesTags(setOf("i"), matchAll = false))
        assertFalse(unicode.matchesTags(setOf("STRASSE"), matchAll = false))
    }

    @Test fun orderLookupIsCaseInsensitiveAndHasNoImplicitFallback() {
        assertEquals(9L, task.orderInCategory("WORK"))
        assertEquals(-2L, task.orderInCategory("home"))
        assertNull(task.orderInCategory("missing"))
        assertNull(task.copy(categoryOrders = emptyMap(), sortOrder = 42).orderInCategory("Work"))
    }

    @Test fun firstMatchingEntryStillWinsForCaseVariantKeys() {
        val ambiguous = task.copy(categoryOrders = linkedMapOf("Work" to 9L, "WORK" to 3L))
        assertEquals(9L, ambiguous.orderInCategory("work"))
    }

    @Test fun callerSpecificMissingOrderPoliciesRemainPossibleWithoutChangingStableTies() {
        val missing = task.copy(id = "missing", categoryOrders = emptyMap(), sortOrder = -10)
        val linked = task.copy(id = "linked", categoryOrders = mapOf("Work" to 2L), sortOrder = 10)
        val list = listOf(missing, linked)
        assertEquals(listOf("linked", "missing"), list.sortedBy { it.orderInCategory("work") ?: Long.MAX_VALUE }.map { it.id })
        assertEquals(listOf("missing", "linked"), list.sortedBy { it.orderInCategory("work") ?: it.sortOrder }.map { it.id })
        assertEquals(listOf("linked"), list.filter { it.orderInCategory("work") != null }.map { it.id })
        val tied = listOf(linked.copy(id = "first"), linked.copy(id = "second"))
        assertEquals(tied, tied.sortedBy { it.orderInCategory("work") })
    }

    @Test fun matchesTheOriginalPredicateAcrossTagCombinations() {
        val names = listOf("Work", "home", "missing", " Work ", "İ")
        val sets = (0 until (1 shl names.size)).map { mask -> names.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet() }
        for (stored in sets) for (requested in sets) for (matchAll in listOf(false, true)) {
            val value = task.copy(categoryNames = stored)
            val hits = requested.map { name -> stored.any { it.equals(name, ignoreCase = true) } }
            val expected = if (matchAll) hits.all { it } else hits.any { it }
            assertEquals("stored=$stored requested=$requested matchAll=$matchAll", expected, value.matchesTags(requested, matchAll))
        }
    }
}
