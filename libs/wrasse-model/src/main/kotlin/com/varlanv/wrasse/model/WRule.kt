package com.varlanv.wrasse.model

/**
 * Interface for all rules.
 * Implementations should keep performance in mind and keep allocations as minimal as possible.
 * If a rule genuinely needs to perform heavy work and requires allocation, design should be revisited to pass
 * pre-computed data from upstream.
 */
interface WRule {
    val id: String

    /**
     * Checks file for constraints violations.
     * Receives mutable list by design to avoid extra per-rule allocations.
     */
    fun check(file: WFile, violations: MutableList<WViolation>)
}
