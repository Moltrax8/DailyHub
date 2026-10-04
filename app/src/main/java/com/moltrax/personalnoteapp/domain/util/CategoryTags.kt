package com.moltrax.personalnoteapp.domain.util

/**
 * Pure multi-tag merge math (Phase 2, JVM-testable).
 *
 * Tag sets union (a tag added on either side survives); per-link positions come
 * from the merge winner, missing ones fall back to the loser. Convergent: both
 * devices compute the same union from the same inputs.
 */
fun mergeCategoryTags(
    winnerNames: Set<String>,
    winnerOrders: Map<String, Long>,
    otherNames: Set<String>,
    otherOrders: Map<String, Long>,
): Pair<Set<String>, Map<String, Long>> {
    val names = winnerNames + otherNames
    val orders = otherOrders + winnerOrders
    return names to orders
}
