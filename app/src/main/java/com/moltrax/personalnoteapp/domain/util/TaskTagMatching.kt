package com.moltrax.personalnoteapp.domain.util

import com.moltrax.personalnoteapp.domain.model.Task

/** Empty ANY matches nothing; empty ALL matches everything. UI callers keep their empty-selection guards. */
internal fun Task.matchesTags(names: Set<String>, matchAll: Boolean): Boolean {
    val hasTag: (String) -> Boolean = { name -> categoryNames.any { it.equals(name, ignoreCase = true) } }
    return if (matchAll) names.all(hasTag) else names.any(hasTag)
}

/** No fallback: each caller decides whether a missing link is excluded or uses another order. */
internal fun Task.orderInCategory(name: String): Long? =
    categoryOrders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
