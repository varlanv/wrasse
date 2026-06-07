package com.varlanv.wrasse.model

interface WRule {
    val id: String

    /**
     * Checks file for constraints violations.
     * Receives mutable list by design to avoid extra per-rule allocations.
     */
    fun check(file: WFile, violations: MutableList<WViolation>)
}
