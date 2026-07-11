package com.varlanv.wrasse.model

/**
 * Interface for all rules.
 * Implementations should keep performance in mind and keep allocations as minimal as possible.
 * If a rule genuinely needs to perform heavy work and requires allocation, design should be revisited to pass
 * pre-computed data from upstream.
 */
interface WUninitializedRule {
    val id: String

    fun initRule(config: WrasseRuleConfig): WRule
}

sealed interface WRule {
    val id: String
    val config: WrasseRuleConfig
}

interface WNodeRule : WRule {
    val targetTypes: Set<WNodeType>

    fun visit(node: WNode, reporter: WReporter)
}

interface WFileRule : WRule {
    fun visit(file: WFile, reporter: WReporter)
}
