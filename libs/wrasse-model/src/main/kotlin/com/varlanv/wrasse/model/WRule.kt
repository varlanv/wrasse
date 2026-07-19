package com.varlanv.wrasse.model

/**
 * Two-phase rule construction: declares a rule id, then produces a configured
 * rule instance via [initRule]. The split lets the config layer decide which
 * rules are active before any rule logic is instantiated. [initRule] is called
 * fresh for every file (see [WRuleSet.dispatchForFile]), so each returned instance
 * owns private per-file mutable state that no other file ever sees.
 */
interface WUninitializedRule {
    val id: String

    fun initRule(config: WrasseRuleConfig): WRule
}

/**
 * Base interface for all configured rules. The sealed hierarchy has four leaf types
 * that determine how the rule receives events during the SAX walk:
 *
 * - [WLeafRule] — targeted leaf dispatch by [WNodeType] (O(1)).
 * - [WNodeRule] — enter/exit events for interior nodes, with optional child leaf forwarding.
 * - [WStreamRule] — receives every leaf (cross-cutting; for spacing and indentation).
 * - [WFileRule] — called once after the walk with the post-walk context.
 *
 * [beforeFile] and [afterFile] are lifecycle hooks called around the walk. Since every
 * instance is already fresh for the file it walks, neither hook exists to reset state;
 * [afterFile] remains load-bearing for rules that defer reporting to end-of-file
 * (e.g. accumulation rules), and [beforeFile] is there for setup a future rule might need.
 */
sealed interface WRule {
    val id: String
    val config: WrasseRuleConfig

    /** Called once before the walk begins, on an instance already fresh for this file. */
    fun beforeFile(ctx: WContext) {}

    /** Called after the walk ends. Rules that defer reporting to end-of-file report here. */
    fun afterFile(ctx: WContext, reporter: WReporter) {}
}

/**
 * Fires on leaf tokens whose type is in [targetTypes]. Dispatched via an ordinal-indexed
 * array — only matching leaves trigger the callback. Most common rule type (~40 rules).
 *
 * Use for rules that check a specific token and its ancestor context:
 * comment-spacing, naming checks, nullable-type-spacing, etc.
 */
interface WLeafRule : WRule {
    val targetTypes: Set<WNodeType>

    fun visitLeaf(ctx: WContext, reporter: WReporter)
}

/**
 * Fires on enter and exit of interior nodes whose type is in [targetTypes].
 *
 * [enterNode] returns true to receive [onChildLeaf] callbacks for every descendant
 * leaf inside the node, plus an [exitNode] call when the node closes. Return false
 * to skip child forwarding (optimization when the enter check alone is sufficient).
 *
 * Use for rules that inspect a node's children: no-wildcard-imports (check for MUL child),
 * modifier-order (check child ordering), empty-block detection, etc.
 */
interface WNodeRule : WRule {
    val targetTypes: Set<WNodeType>

    fun enterNode(ctx: WContext, reporter: WReporter): Boolean = true

    /** Called for every descendant leaf inside an entered node. */
    fun onChildLeaf(ctx: WContext, reporter: WReporter) {}
    fun exitNode(ctx: WContext, reporter: WReporter) {}
}

/**
 * Extension of [WNodeRule] for rules that need access to the direct children list
 * on exit (wrapping rules, argument-list inspection).
 *
 * The framework automatically buffers direct children between enter and exit and
 * provides them as a [ChildBuffer] in the buffered [exitNode] overload.
 */
interface WBufferedNodeRule : WNodeRule {
    fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter)

    override fun exitNode(ctx: WContext, reporter: WReporter) {}
}

/**
 * Fires on every leaf event with no type filtering. Also receives [enterNode]/[exitNode]
 * notifications for interior node boundaries.
 *
 * The most expensive rule type — keep the count small. Use for rules that need
 * cross-cutting leaf access: spacing rules (check adjacent tokens via prevLeaf),
 * indentation (track indent depth on enter/exit), semicolons (deferred forward lookup).
 */
interface WStreamRule : WRule {
    fun visitLeaf(ctx: WContext, reporter: WReporter)
    fun enterNode(ctx: WContext) {}
    fun exitNode(ctx: WContext) {}
}

/**
 * Called once after the SAX walk completes with the post-walk [WContext].
 * For rules that check whole-file properties: trailing-newline (via prevLeafText),
 * max-line-length (via offset tracking), etc.
 */
interface WFileRule : WRule {
    fun visit(ctx: WContext, reporter: WReporter)
}
