package com.varlanv.wrasse.model

/**
 * Collects violations reported by rules during a SAX walk.
 * Takes raw offsets — no dependency on a node object.
 * The [rule] parameter lets the reporter read the rule's configured severity level.
 */
interface WReporter {

    val reports: List<ViolationReport>

    fun report(ruleId: String, message: String, startOffset: Int, endOffset: Int, rule: WRule)
}
