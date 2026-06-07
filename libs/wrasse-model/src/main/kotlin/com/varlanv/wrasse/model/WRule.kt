package com.varlanv.wrasse.model

/**
 * Interface for all rules.
 * Implementations should keep performance in mind and keep allocations as minimal as possible.
 * If a rule genuinely needs to perform heavy work and requires allocation, design should be revisited to pass
 * pre-computed data from upstream.
 */
sealed interface WRule {
    val id: String
}

interface NodeVisitorWRule : WRule {
    val targetTypes: Set<WNodeType>

    fun visit(node: WNode, violations: MutableCollection<WViolation>)
}

interface FileVisitorWRule : WRule {
    fun visit(file: WFile, violations: MutableCollection<WViolation>)
}
