package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit

/**
 * Collects violations and optional fix edits reported by rules during a SAX walk.
 * Takes raw offsets — no dependency on a node object.
 * The [rule] parameter lets the reporter read the rule's configured severity level.
 * A non-empty [edits] list means the violation is autocorrectable.
 */
interface WReporter {
    val reports: List<ViolationReport>

    fun report(ruleId: String, message: String, startOffset: Int, endOffset: Int, rule: WRule, edits: List<WEdit> = emptyList())
}
